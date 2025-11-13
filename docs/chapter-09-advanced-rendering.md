# Chapter 9: Advanced Rendering
## PBR, Shadows & Post-Processing

**What You'll Learn:**
- Physically-Based Rendering (PBR) materials
- Shadow mapping
- Post-processing effects (bloom, SSAO, tone mapping)
- Render graph architecture

**Estimated Time:** 4-5 hours

---

## PBR Materials

### Metallic-Roughness Workflow

```java
public record PBRMaterial(
    Texture albedoMap,
    Texture normalMap,
    Texture metallicMap,
    Texture roughnessMap,
    Texture aoMap,
    Vector3f albedo,
    float metallic,
    float roughness
) implements Component { }
```

### PBR Shader (Fragment)

```glsl
// Cook-Torrance BRDF
vec3 F0 = mix(vec3(0.04), albedo, metallic);
vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

float NDF = DistributionGGX(N, H, roughness);
float G = GeometrySmith(N, V, L, roughness);

vec3 numerator = NDF * G * F;
float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
vec3 specular = numerator / denominator;

vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
vec3 Lo = (kD * albedo / PI + specular) * radiance * NdotL;
```

---

## Shadow Mapping

### Depth Pass

```java
// 1. Render scene from light's perspective to depth texture
renderer.bindFramebuffer(shadowMapFBO);
renderer.setViewProjection(lightViewMatrix, lightProjMatrix);
world.query(MeshRenderer.class, Transform3D.class).forEach(entity -> {
    renderer.renderDepthOnly(entity);
});

// 2. Main pass: sample shadow map
renderer.bindFramebuffer(mainFBO);
renderer.setViewProjection(cameraView, cameraProj);
renderer.bindTexture(shadowMap, SHADOW_MAP_SLOT);
world.query(MeshRenderer.class, Transform3D.class).forEach(entity -> {
    renderer.render(entity); // Shader samples shadow map
});
```

### PCF Shadow Softening

```glsl
float shadow = 0.0;
vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
for(int x = -1; x <= 1; ++x) {
    for(int y = -1; y <= 1; ++y) {
        float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
        shadow += currentDepth - bias > pcfDepth ? 1.0 : 0.0;
    }
}
shadow /= 9.0;
```

---

## Post-Processing

### Render Graph

```
Scene Render → GBuffer → Lighting → Bloom → Tone Mapping → Output
                  ↓
              Shadow Map
```

### Bloom Effect

```java
// 1. Extract bright pixels
brightPassShader.setUniform("threshold", 1.0f);
renderer.renderFullscreenQuad(sceneTexture, brightTexture);

// 2. Gaussian blur (separable)
for (int i = 0; i < blurPasses; i++) {
    blurShader.setUniform("horizontal", true);
    renderer.renderFullscreenQuad(brightTexture, tempTexture);

    blurShader.setUniform("horizontal", false);
    renderer.renderFullscreenQuad(tempTexture, brightTexture);
}

// 3. Composite
compositeShader.bind();
compositeShader.setTexture("scene", sceneTexture);
compositeShader.setTexture("bloom", brightTexture);
renderer.renderFullscreenQuad();
```

### Tone Mapping

```glsl
// Reinhard tone mapping
vec3 mapped = hdrColor / (hdrColor + vec3(1.0));

// Gamma correction
mapped = pow(mapped, vec3(1.0/2.2));
```

---

## Exercises

1. Implement cascaded shadow maps for large scenes
2. Add screen-space reflections (SSR)
3. Implement deferred rendering
4. Add temporal anti-aliasing (TAA)
5. Create volumetric fog

---

**Previous:** [← Chapter 8 - Editor](chapter-08-editor-basics.md)
**Next:** [Chapter 10 - Physics →](chapter-10-physics.md)
