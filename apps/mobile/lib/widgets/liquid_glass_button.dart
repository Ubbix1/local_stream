import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'liquid_glass_presets.dart';

/// A liquid-glass-styled button for icon or icon + text content.
///
/// Wraps [GlassButton.custom]. Accepts the same [VoidCallback?] contract as
/// Material buttons: `onPressed: null` renders the button disabled. `enabled`
/// forces a disabled state regardless of [onPressed].
class LiquidGlassButton extends StatelessWidget {
  const LiquidGlassButton({
    super.key,
    this.label,
    this.icon,
    required this.onPressed,
    this.enabled = true,
    this.height = 48,
    this.borderRadius = 12,
    this.settings,
    this.glowColor,
  });

  /// Optional text shown inside the button.
  final String? label;

  /// Optional icon shown inside the button.
  final IconData? icon;

  /// Callback when the button is pressed. `null` renders the button disabled.
  final VoidCallback? onPressed;

  /// Force-enables/[disables] the button beyond [onPressed].
  final bool enabled;

  /// Height of the button. Width is delegated to the parent (matches `null`
  /// behavior of [GlassButton.custom]).
  final double height;

  /// Corner radius of the glass shape. Defaults to 12 (Material filled buttons).
  final double borderRadius;

  /// Glass effect override. Defaults to the package's native light/dark
  /// appearance (needs [GlassQuality.premium], which the widget sets).
  final LiquidGlassSettings? settings;

  /// Color of the interaction glow. Defaults to the widget default.
  final Color? glowColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isEnabled = enabled && onPressed != null;

    final content = Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 20, color: isEnabled ? Colors.white : null),
          if (label != null) const SizedBox(width: 8),
        ],
        if (label != null)
          Text(
            label!,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: isEnabled ? Colors.white : theme.disabledColor,
            ),
          ),
      ],
    );

    return GlassButton.custom(
      onTap: isEnabled ? onPressed! : () {},
      enabled: isEnabled,
      label: label ?? '',
      shape: LiquidRoundedSuperellipse(borderRadius: borderRadius),
      height: height,
      quality: GlassQuality.premium,
      useOwnLayer: true,
      settings: settings ?? liquidGlassSettingsFor(theme.brightness),
      glowColor: glowColor,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: content,
      ),
    );
  }
}
