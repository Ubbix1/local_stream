import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

/// Dark-appearance liquid glass recipe, tuned for the app's GitHub-dark
/// surface. A translucent white veil plus pronounced specular, Fresnel rim,
/// refraction and ambient lift so the surface reads as real glass over a flat
/// background instead of a solid tinted panel.
const LiquidGlassSettings liquidGlassDarkSettings = LiquidGlassSettings(
  glassColor: Color(0x26FFFFFF),
  thickness: 28,
  blur: 6,
  lightIntensity: 1.0,
  ambientStrength: 0.15,
  fresnelStrength: 1.0,
  refractiveIndex: 1.24,
  saturation: 1.4,
  glowIntensity: 0.8,
  chromaticAberration: 0.01,
  edgeAbsorption: 0.12,
  bodyMode: GlassBodyMode.adaptive,
);

/// Light-appearance liquid glass recipe.
const LiquidGlassSettings liquidGlassLightSettings = LiquidGlassSettings(
  glassColor: Color(0x59FFFFFF),
  thickness: 28,
  blur: 6,
  lightIntensity: 0.9,
  ambientStrength: 0.1,
  fresnelStrength: 1.0,
  refractiveIndex: 1.2,
  saturation: 1.0,
  glowIntensity: 0.8,
  chromaticAberration: 0.01,
  bodyMode: GlassBodyMode.adaptive,
);

/// Picks the matching preset for a [Brightness].
LiquidGlassSettings liquidGlassSettingsFor(Brightness brightness) =>
    brightness == Brightness.dark
        ? liquidGlassDarkSettings
        : liquidGlassLightSettings;