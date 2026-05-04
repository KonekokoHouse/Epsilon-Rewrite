#version 410 core

out vec2 vUv;

#ifdef VULKAN
const vec2 FULLSCREEN_VERTICES[6] = vec2[](
    vec2(-1.0,  1.0),
    vec2(-1.0, -1.0),
    vec2( 1.0, -1.0),
    vec2(-1.0,  1.0),
    vec2( 1.0, -1.0),
    vec2( 1.0,  1.0)
);
#else
const vec2 FULLSCREEN_VERTICES[6] = vec2[](
    vec2(-1.0, -1.0),
    vec2(-1.0,  1.0),
    vec2( 1.0,  1.0),
    vec2(-1.0, -1.0),
    vec2( 1.0,  1.0),
    vec2( 1.0, -1.0)
);
#endif

void main() {
    vec2 position = FULLSCREEN_VERTICES[gl_VertexID % 6];
    vUv = position * 0.5 + 0.5;
    gl_Position = vec4(position, 0.0, 1.0);
}