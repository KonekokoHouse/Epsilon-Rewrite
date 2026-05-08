#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_Bounds;
in vec4 f_Radius;
in float f_BlurRadius;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfSize = (f_Bounds.zw - f_Bounds.xy) * 0.5;
    vec2 center = (f_Bounds.xy + f_Bounds.zw) * 0.5;
    vec2 p = f_Position - center;

    float r = 0.0;
    if (p.x > 0.0) {
        // Right
        if (p.y > 0.0) r = f_Radius.z; // Bottom-Right
        else r = f_Radius.y;           // Top-Right
    } else {
        // Left
        if (p.y > 0.0) r = f_Radius.w; // Bottom-Left
        else r = f_Radius.x;           // Top-Left
    }

    vec2 q = abs(p) - halfSize + r;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;

    float normalizedDist = clamp(dist / f_BlurRadius, 0.0, 1.0);
    float shadowAlpha = pow(1.0 - normalizedDist, 1.5); 

    float delta = fwidth(dist);
    float insideAlpha = smoothstep(-delta, 0.0, dist);
    
    shadowAlpha *= insideAlpha;

    if (shadowAlpha <= 0.0) discard;

    fragColor = vec4(f_Color.rgb, f_Color.a * shadowAlpha);
}
