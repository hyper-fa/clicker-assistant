import { AccessibilityBridge } from './AccessibilityBridge';

export type RunningListener = (running: boolean) => void;

export class ClickEngine {
  private static running = false;
  private static intervalMs = 100;
  private static targetX = 0;
  private static targetY = 0;
  private static timerId = -1;
  private static listener?: RunningListener;

  static isRunning(): boolean {
    return ClickEngine.running;
  }

  static setRunningListener(listener: RunningListener): void {
    ClickEngine.listener = listener;
  }

  static configure(x: number, y: number, intervalMs: number): void {
    ClickEngine.targetX = Math.max(0, Math.round(x));
    ClickEngine.targetY = Math.max(0, Math.round(y));
    ClickEngine.intervalMs = Math.max(1, Math.round(intervalMs));
  }

  static start(): boolean {
    if (!AccessibilityBridge.isEnabled()) {
      return false;
    }
    if (ClickEngine.running) {
      return true;
    }
    ClickEngine.running = true;
    ClickEngine.listener?.(true);
    ClickEngine.scheduleNext(0);
    return true;
  }

  static stop(): void {
    ClickEngine.running = false;
    if (ClickEngine.timerId >= 0) {
      clearTimeout(ClickEngine.timerId);
      ClickEngine.timerId = -1;
    }
    ClickEngine.listener?.(false);
  }

  static toggle(): boolean {
    if (ClickEngine.running) {
      ClickEngine.stop();
      return true;
    }
    return ClickEngine.start();
  }

  private static scheduleNext(delayMs: number): void {
    ClickEngine.timerId = setTimeout(async () => {
      if (!ClickEngine.running) {
        return;
      }
      await AccessibilityBridge.tap(ClickEngine.targetX, ClickEngine.targetY);
      if (ClickEngine.running) {
        ClickEngine.scheduleNext(ClickEngine.intervalMs);
      }
    }, delayMs);
  }
}
