import AccessibilityExtensionAbility from '@ohos.application.AccessibilityExtensionAbility';
import type { AccessibilityEvent, AccessibilityExtensionContext } from '@ohos.application.AccessibilityExtensionAbility';
import hilog from '@ohos.hilog';
import { AccessibilityBridge } from '../model/AccessibilityBridge';

const DOMAIN = 0xBABA;

export default class ClickAccessibilityAbility extends AccessibilityExtensionAbility {
  onConnect(): void {
    hilog.info(DOMAIN, 'ClickAccessibilityAbility', 'onConnect');
    AccessibilityBridge.setContext(this.context as AccessibilityExtensionContext);
  }

  onDisconnect(): void {
    hilog.info(DOMAIN, 'ClickAccessibilityAbility', 'onDisconnect');
    AccessibilityBridge.clearContext(this.context as AccessibilityExtensionContext);
  }

  onAccessibilityEvent(event: AccessibilityEvent): void {
    hilog.debug(DOMAIN, 'ClickAccessibilityAbility', 'event: %{public}s', JSON.stringify(event));
  }
}
