#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;

layout(location = 0) out vec4 fragColor;

vec4 normalizeRadii(vec2 size, vec4 r) {
    r = max(r, 0.0);

    float sTop = size.x / max(r.x + r.y, 1e-5);
    float sBottom = size.x / max(r.w + r.z, 1e-5);
    float sLeft = size.y / max(r.x + r.w, 1e-5);
    float sRight = size.y / max(r.y + r.z, 1e-5);

    float scale = min(1.0, min(min(sTop, sBottom), min(sLeft, sRight)));
    return r * scale;
}

float cornerRadius(vec2 p, vec4 radii) {
    // radii order: TL, TR, BR, BL
    bool right = p.x >= 0.0;
    bool bottom = p.y >= 0.0;
    if (!right && !bottom) return radii.x;
    if (right && !bottom) return radii.y;
    if (right) return radii.z;
    return radii.w;
}

float sdRoundRect(vec2 p, vec2 size, vec4 radii) {
    float rCurrent = cornerRadius(p, radii);
    vec2 q = abs(p) - (size * 0.5 - vec2(rCurrent));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rCurrent;
}

float coverage2x2(vec2 p, vec2 size, vec4 radii) {
    vec2 dx = dFdx(p);
    vec2 dy = dFdy(p);

    vec2 o00 = (-0.25 * dx) + (-0.25 * dy);
    vec2 o10 = ( 0.25 * dx) + (-0.25 * dy);
    vec2 o01 = (-0.25 * dx) + ( 0.25 * dy);
    vec2 o11 = ( 0.25 * dx) + ( 0.25 * dy);

    float c = 0.0;
    c += sdRoundRect(p + o00, size, radii) <= 0.0 ? 1.0 : 0.0;
    c += sdRoundRect(p + o10, size, radii) <= 0.0 ? 1.0 : 0.0;
    c += sdRoundRect(p + o01, size, radii) <= 0.0 ? 1.0 : 0.0;
    c += sdRoundRect(p + o11, size, radii) <= 0.0 ? 1.0 : 0.0;
    return c * 0.25;
}

void main() {
    vec2 size = f_InnerRect.zw - f_InnerRect.xy;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = f_Position - center;

    vec4 radii = normalizeRadii(size, f_Radius);
    float dist = sdRoundRect(p, size, radii);

    float aa = max(fwidth(dist), 1e-4);
    float analyticAlpha = clamp(0.5 - dist / aa, 0.0, 1.0);
    float sampledCoverage = coverage2x2(p, size, radii);
    float alpha = mix(analyticAlpha, sampledCoverage, 0.85);

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
}
