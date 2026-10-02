package com.tk.filmcam.gl

import com.tk.filmcam.R

/**
 * Fragment shader for the film look. Order matters and matches the reference
 * pipeline research: tonality (contrast, fade) before colour, then optical
 * effects (halation, softness), then grain, then vignette.
 */
object FilmShader {

    val FRAGMENT: String = """
precision highp float;

varying vec2 vTexCoord;

uniform sampler2D uTex;
uniform vec2  uResolution;
uniform float uTime;

uniform vec3  uGain;
uniform float uSaturation;
uniform float uContrast;
uniform float uFade;
uniform float uGrain;
uniform float uVignette;
uniform float uWarmth;
uniform float uHalation;
uniform float uSoftness;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 sampleTex(vec2 uv) {
    return texture2D(uTex, uv).rgb;
}

void main() {
    vec2 uv = vTexCoord;
    vec2 texel = 1.0 / uResolution;

    // --- optical softness: cheap 5-tap cross blur, only when the look wants it
    vec3 color = sampleTex(uv);
    if (uSoftness > 0.001) {
        vec3 blur = color * 0.36;
        blur += sampleTex(uv + vec2(texel.x, 0.0) * 2.0) * 0.16;
        blur += sampleTex(uv - vec2(texel.x, 0.0) * 2.0) * 0.16;
        blur += sampleTex(uv + vec2(0.0, texel.y) * 2.0) * 0.16;
        blur += sampleTex(uv - vec2(0.0, texel.y) * 2.0) * 0.16;
        color = mix(color, blur, uSoftness);
    }

    // --- halation: highlights bleed into the surroundings, CCD/compact signature
    if (uHalation > 0.001) {
        vec3 bleed = vec3(0.0);
        float radius = 3.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7853981634;
            vec2 off = vec2(cos(a), sin(a)) * texel * radius;
            vec3 s = sampleTex(uv + off);
            // keep only bright areas
            float l = dot(s, LUMA);
            bleed += max(vec3(0.0), s - 0.62) * smoothstep(0.62, 1.0, l);
        }
        bleed /= 8.0;
        color += bleed * uHalation * 2.2;
    }

    // --- tonality FIRST: contrast around mid pivot, then lifted blacks (fade)
    color = (color - 0.5) * uContrast + 0.5;
    color = color * (1.0 - uFade) + uFade;

    // --- colour: saturation, per-channel gain, warmth
    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, uSaturation);
    color *= uGain;
    color.r += uWarmth * 0.08;
    color.b -= uWarmth * 0.08;

    // --- grain: luminance-dependent, stronger in midtones like real film
    float g = hash12(uv * uResolution + vec2(uTime * 61.7, uTime * 37.3));
    float midWeight = 1.0 - abs(luma - 0.5) * 1.6;
    color += (g - 0.5) * uGrain * 0.32 * max(midWeight, 0.25);

    // --- vignette: radial darkening, shaped so corners fall off smoothly
    vec2 centered = uv - 0.5;
    float r = length(centered * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0));
    float vig = smoothstep(0.95, 0.28, r);
    color *= mix(1.0, vig, uVignette);

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    }