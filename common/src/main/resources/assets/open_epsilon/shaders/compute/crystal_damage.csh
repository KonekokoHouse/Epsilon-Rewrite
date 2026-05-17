#version 450

/*
 * gl_WorkGroupID.x   = taskId（每个 workgroup 处理一个任务）
 * gl_LocalInvocationID.x = rayId（每个线程最多追踪一条射线）
 */

layout(local_size_x = 64) in;

layout(std430, set = 0, binding = 0) readonly buffer VoxelGrid {
    ivec4 header;       // xyz = gridOrigin, w = gridSize
    uint  voxelBits[];
} grid;

struct Task {
    vec4 crystalPos;    // xyz = 爆炸中心, w = 半径
    vec4 targetPos;     // xyz = 目标脚底, w = unused
    vec4 targetSize;    // x = halfWidth, y = height
    vec4 params;        // x = armor, y = toughness, z = enchantProt, w = difficulty
    vec4 extra;         // x = resistanceMultiplier, y = applyDifficulty(0/1)
};

layout(std430, set = 0, binding = 1) readonly buffer TaskBuffer {
    uint taskCount;
    uint _pad0;
    uint _pad1;
    uint _pad2;
    Task tasks[];
} taskBuf;

layout(std430, set = 0, binding = 2) writeonly buffer ResultBuffer {
    float damages[];
} resultBuf;

const float EPSILON = 1e-6;
const float TRACE_EPSILON = 1e-4;
const float RAYMARCH_STEP_SIZE = 0.1;
const int   MAX_RAYMARCH_STEPS = 96;

shared float s_weightedHit[64];
shared float s_weight[64];

// 采样体素值（0 或 1），越界返回 0
float sampleVoxel(ivec3 worldPos) {
    ivec3 origin = grid.header.xyz;
    int   size   = grid.header.w;
    ivec3 local  = worldPos - origin;

    vec3 localF = vec3(local);
    float maxCoord = float(size - 1);
    float valid =
        step(0.0, localF.x) * step(localF.x, maxCoord) *
        step(0.0, localF.y) * step(localF.y, maxCoord) *
        step(0.0, localF.z) * step(localF.z, maxCoord);

    ivec3 safe = clamp(local, ivec3(0), ivec3(size - 1));
    int flatIdx = (safe.z * size + safe.y) * size + safe.x;
    uint word = grid.voxelBits[flatIdx >> 5];
    float bit = float((word >> (flatIdx & 31)) & 1u);

    return valid * bit;
}

// Raymarching
float traceRay(vec3 origin, vec3 target) {
    vec3 baseDir = target - origin;
    float rawDist = length(baseDir);
    float rayActive = step(EPSILON, rawDist);

    vec3 dir = baseDir / max(rawDist, EPSILON);
    vec3 startPos = origin + dir * TRACE_EPSILON;
    vec3 endPos = target - dir * TRACE_EPSILON;
    float maxDist = max(distance(startPos, endPos), 0.0);

    float blocked = 0.0;
    for (int i = 0; i < MAX_RAYMARCH_STEPS; i++) {
        float traveled = (float(i) + 0.5) * RAYMARCH_STEP_SIZE;
        float marchMask = step(traveled, maxDist) * (1.0 - blocked) * rayActive;
        vec3 samplePos = startPos + dir * traveled;
        float solid = sampleVoxel(ivec3(floor(samplePos)));
        blocked = mix(blocked, 1.0, clamp(solid * marchMask, 0.0, 1.0));
    }

    return mix(1.0, 1.0 - blocked, rayActive);
}

float applyArmor(float damage, float armor, float toughness) {
    float t = 2.0 + toughness / 4.0;
    float effective = clamp(armor - damage / t, armor * 0.2, 20.0);
    return damage * (1.0 - effective / 25.0);
}

float applyDifficulty(float damage, float difficulty) {
    float peaceful = 0.0;
    float easy     = min(damage * 0.5 + 1.0, damage);
    float normal   = damage;
    float hard     = damage * 1.5;

    float isEasy   = step(0.5, difficulty) * step(difficulty, 1.5);
    float isNormal = step(1.5, difficulty) * step(difficulty, 2.5);
    float isHard   = step(2.5, difficulty);

    return peaceful * (1.0 - step(0.5, difficulty))
         + easy     * isEasy
         + normal   * isNormal
         + hard     * isHard;
}

void main() {
    uint taskId = gl_WorkGroupID.x;
    uint rayId  = gl_LocalInvocationID.x;

    s_weightedHit[rayId] = 0.0;
    s_weight[rayId]      = 0.0;

    if (taskId >= taskBuf.taskCount) {
        return;
    }

    Task task = taskBuf.tasks[taskId];

    vec3  crystalPos  = task.crystalPos.xyz;
    float radius      = task.crystalPos.w;
    vec3  targetPos   = task.targetPos.xyz;
    float halfWidth   = task.targetSize.x;
    float height      = task.targetSize.y;

    vec3 bbMin = targetPos - vec3(halfWidth, 0.0, halfWidth);
    vec3 bbMax = targetPos + vec3(halfWidth, height, halfWidth);
    vec3 bbSize = bbMax - bbMin;
    vec3 invSteps = bbSize * 2.0 + vec3(1.0);
    vec3 stepSize = vec3(1.0) / invSteps;

    vec3 offset = vec3(
        (1.0 - floor(1.0 / stepSize.x) * stepSize.x) * 0.5,
        0.0,
        (1.0 - floor(1.0 / stepSize.z) * stepSize.z) * 0.5
    );

    int nx = int(floor(1.0 / stepSize.x)) + 1;
    int ny = int(floor(1.0 / stepSize.y)) + 1;
    int nz = int(floor(1.0 / stepSize.z)) + 1;
    uint totalSamples = uint(nx * ny * nz);
    float validSteps = step(0.0, stepSize.x) * step(0.0, stepSize.y) * step(0.0, stepSize.z);

    float sampleActive = step(float(rayId), float(totalSamples - 1u));

    uint sampleId = min(rayId, totalSamples - 1u);
    int sid = int(sampleId);
    int nxy = nx * ny;
    int iz = sid / nxy;
    int rem = sid - iz * nxy;
    int iy = rem / nx;
    int ix = rem - iy * nx;

    float xx = float(ix) * stepSize.x;
    float yy = float(iy) * stepSize.y;
    float zz = float(iz) * stepSize.z;

    vec3 samplePos = vec3(
        mix(bbMin.x, bbMax.x, xx) + offset.x,
        mix(bbMin.y, bbMax.y, yy),
        mix(bbMin.z, bbMax.z, zz) + offset.z
    );

    float vis = traceRay(samplePos, crystalPos);
    s_weightedHit[rayId] = vis * sampleActive;
    s_weight[rayId] = sampleActive;

    s_weightedHit[rayId] *= validSteps;
    s_weight[rayId]      *= validSteps;

    barrier();

    if (rayId == 0u) {
        float totalWeightedHit = 0.0;
        float totalWeight      = 0.0;
        for (int i = 0; i < int(gl_WorkGroupSize.x); i++) {
            totalWeightedHit += s_weightedHit[i];
            totalWeight      += s_weight[i];
        }

        float exposure = totalWeightedHit / max(totalWeight, EPSILON);

        float doubleRadius = radius * 2.0;
        float dist = distance(targetPos, crystalPos) / doubleRadius;
        float inRange = step(dist, 1.0);

        float impact = (1.0 - dist) * exposure;
        float baseDamage = (impact * impact + impact) * 0.5 * 7.0 * doubleRadius + 1.0;

        float totalArmor  = task.params.x;
        float armorTough  = task.params.y;
        float enchantProt = task.params.z;
        float difficulty  = task.params.w;
        float resistanceMul = task.extra.x;
        float applyDifficultyFlag = clamp(task.extra.y, 0.0, 1.0);

        float difficultyScaled = applyDifficulty(baseDamage, difficulty);
        baseDamage = mix(baseDamage, difficultyScaled, applyDifficultyFlag);
        baseDamage = applyArmor(baseDamage, totalArmor, armorTough);
        baseDamage *= resistanceMul;

        float enchantClamped = clamp(enchantProt, 0.0, 20.0);
        baseDamage *= (1.0 - enchantClamped / 25.0);

        float finalDamage = max(baseDamage * inRange * step(EPSILON, exposure), 0.0);
        resultBuf.damages[taskId] = finalDamage;
    }
}
