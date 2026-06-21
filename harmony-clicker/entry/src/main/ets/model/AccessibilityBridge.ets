import type { AccessibilityExtensionContext } from '@ohos.application.AccessibilityExtensionAbility';
import GesturePath from '@ohos.accessibility.GesturePath';
import GesturePoint from '@ohos.accessibility.GesturePoint';
import hilog from '@ohos.hilog';

const DOMAIN = 0xBABA;

export class AccessibilityBridge {
  private static context?: AccessibilityExtensionContext;

  static setContext(context: AccessibilityExtensionContext): void {
    AccessibilityBridge.context = context;
  }

  static clearContext(context: AccessibilityExtensionContext): void {
    if (AccessibilityBridge.context === context) {
      AccessibilityBridge.context = undefined;
    }
  }

  static isEnabled(): boolean {
    return AccessibilityBridge.context !== undefined;
  }

  static async tap(x: number, y: number, durationMs: number = 50): Promise<boolean> {
    const context = AccessibilityBridge.context;
    if (!context) {
      return false;
    }

    const gesturePoint = new GesturePoint.GesturePoint(Math.round(x), Math.round(y));
    const gesturePath = new GesturePath.GesturePath(Math.max(1, Math.round(durationMs)));
    gesturePath.points.push(gesturePoint);

    try {
      await context.injectGesture(gesturePath);
      return true;
    } catch (error) {
      hilog.error(DOMAIN, 'AccessibilityBridge', 'tap failed');
      return false;
    }
  }
}
