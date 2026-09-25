import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'liquid_glass_presets.dart';

/// A liquid-glass-styled icon-only button.
///
/// Used for controls where an icon is the only visual content, e.g.
/// floating action buttons, copy/favorite buttons. `onPressed: null` renders
/// the button disabled.
class LiquidGlassIconButton extends StatelessWidget {
  const LiquidGlassIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.size = 44,
    this.tooltip,
    this.shape = GlassIconButtonShape.circle,
    this.settings,
  });

  /// The icon to display.
  final IconData icon;

  /// Callback when the button is pressed. `null` renders the button disabled.
  final VoidCallback? onPressed;

  /// Size (width and height) of the button in logical pixels.
  final double size;

  /// Accessible name for screen readers. Recommended for icon-only buttons.
  final String? tooltip;

  /// Shape of the button. Defaults to [GlassIconButtonShape.circle].
  final GlassIconButtonShape shape;

  /// Glass effect override. Defaults to the package's native light/dark
  /// appearance (needs [GlassQuality.premium], which the widget sets).
  final LiquidGlassSettings? settings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GlassIconButton(
      icon: Icon(icon),
      onPressed: onPressed,
      size: size,
      shape: shape,
      semanticLabel: tooltip,
      quality: GlassQuality.premium,
      useOwnLayer: true,
      settings: settings ?? liquidGlassSettingsFor(theme.brightness),
    );
  }
}
