import common from '@ohos.app.ability.common';
import display from '@ohos.display';
import promptAction from '@ohos.promptAction';
import { AppContext } from '../model/AppContext';
import { AccessibilityBridge } from '../model/AccessibilityBridge';
import { ClickEngine } from '../model/ClickEngine';
import { ClickSettings, ClickTarget } from '../model/ClickSettings';

@Entry
@Component
struct Index {
  @State intervalText: string = '100';
  @State targetXText: string = '0';
  @State targetYText: string = '0';
  @State targetText: string = '未设置';
  @State running: boolean = false;
  @State accessibilityStatusText: string = '未开启';
  @State captureTip: string = '';

  private target: ClickTarget = { x: 0, y: 0 };

  aboutToAppear(): void {
    ClickEngine.setRunningListener((running: boolean) => {
      this.running = running;
      this.refreshStatus();
    });
    this.loadSettings();
    this.refreshStatus();
  }

  private getContext(): common.UIAbilityContext | undefined {
    return AppContext.uiAbilityContext;
  }

  private async loadSettings(): Promise<void> {
    const context = this.getContext();
    if (!context) {
      return;
    }
    const interval = await ClickSettings.getIntervalMs(context);
    this.intervalText = interval.toString();
    this.target = await ClickSettings.getTarget(context);
    this.targetXText = Math.round(this.target.x).toString();
    this.targetYText = Math.round(this.target.y).toString();
    this.targetText = this.formatTarget(this.target);
  }

  private refreshStatus(): void {
    this.running = ClickEngine.isRunning();
    this.accessibilityStatusText = AccessibilityBridge.isEnabled() ? '已开启' : '未开启';
  }

  private parseInterval(): number {
    const value = Number(this.intervalText.trim());
    if (!Number.isFinite(value) || value <= 0) {
      return 100;
    }
    return Math.max(1, Math.round(value));
  }

  private formatTarget(target: ClickTarget): string {
    if (target.x <= 0 && target.y <= 0) {
      return '未设置';
    }
    return `X ${Math.round(target.x)} / Y ${Math.round(target.y)}`;
  }

  private parseCoordinate(value: string): number {
    const coordinate = Number(value.trim());
    if (!Number.isFinite(coordinate) || coordinate < 0) {
      return 0;
    }
    return Math.round(coordinate);
  }

  private async saveInterval(): Promise<number> {
    const context = this.getContext();
    const interval = this.parseInterval();
    this.intervalText = interval.toString();
    if (context) {
      await ClickSettings.setIntervalMs(context, interval);
    }
    return interval;
  }

  private async captureCurrentCenter(): Promise<void> {
    const context = this.getContext();
    if (!context) {
      promptAction.showToast({ message: '上下文未就绪' });
      return;
    }

    this.captureTip = '3 秒后记录屏幕中心点，请切到目标画面并对准位置';
    promptAction.showToast({ message: '3 秒后记录屏幕中心点' });
    setTimeout(async () => {
      const defaultDisplay = await display.getDefaultDisplay();
      this.target = {
        x: Math.round(defaultDisplay.width / 2),
        y: Math.round(defaultDisplay.height / 2)
      };
      await ClickSettings.setTarget(context, this.target);
      this.targetXText = this.target.x.toString();
      this.targetYText = this.target.y.toString();
      this.targetText = this.formatTarget(this.target);
      this.captureTip = '已记录屏幕中心点；如需更准确位置，请手动微调坐标';
      promptAction.showToast({ message: `已记录 ${this.targetText}` });
    }, 3000);
  }

  private async startOrStop(): Promise<void> {
    this.refreshStatus();
    if (ClickEngine.isRunning()) {
      ClickEngine.stop();
      promptAction.showToast({ message: '已停止' });
      return;
    }

    if (!AccessibilityBridge.isEnabled()) {
      promptAction.showToast({ message: '请先在系统设置中开启奔奔助手无障碍服务' });
      return;
    }

    const interval = await this.saveInterval();
    this.target = {
      x: this.parseCoordinate(this.targetXText),
      y: this.parseCoordinate(this.targetYText)
    };
    const context = this.getContext();
    if (context) {
      await ClickSettings.setTarget(context, this.target);
    }
    this.targetText = this.formatTarget(this.target);
    ClickEngine.configure(this.target.x, this.target.y, interval);
    if (!ClickEngine.start()) {
      promptAction.showToast({ message: '启动失败，请检查无障碍权限' });
      return;
    }
    promptAction.showToast({ message: '已开始点击' });
  }

  build() {
    Column({ space: 16 }) {
      Text('奔奔助手')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Start)

      Row() {
        Text('无障碍权限')
          .fontSize(16)
          .fontColor('#334155')
        Text(this.accessibilityStatusText)
          .fontSize(16)
          .fontColor(AccessibilityBridge.isEnabled() ? '#047857' : '#B91C1C')
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)

      Button('刷新状态')
        .width('100%')
        .height(46)
        .onClick(() => this.refreshStatus())

      Text('点击间隔（毫秒）')
        .fontSize(15)
        .fontColor('#334155')
        .width('100%')

      TextInput({ text: this.intervalText, placeholder: '100' })
        .type(InputType.Number)
        .height(46)
        .width('100%')
        .onChange((value: string) => {
          this.intervalText = value;
        })

      Row() {
        Text('目标位置')
          .fontSize(16)
          .fontColor('#334155')
        Text(this.targetText)
          .fontSize(16)
          .fontColor('#0F172A')
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)

      Row({ space: 12 }) {
        TextInput({ text: this.targetXText, placeholder: 'X' })
          .type(InputType.Number)
          .height(46)
          .layoutWeight(1)
          .onChange((value: string) => {
            this.targetXText = value;
          })
        TextInput({ text: this.targetYText, placeholder: 'Y' })
          .type(InputType.Number)
          .height(46)
          .layoutWeight(1)
          .onChange((value: string) => {
            this.targetYText = value;
          })
      }
      .width('100%')

      Button('捕获目标位置')
        .width('100%')
        .height(48)
        .onClick(() => this.captureCurrentCenter())

      Text(this.captureTip)
        .fontSize(13)
        .fontColor('#64748B')
        .width('100%')

      Button(this.running ? '停止点击' : '开始点击')
        .width('100%')
        .height(54)
        .backgroundColor(this.running ? '#DC2626' : '#059669')
        .fontColor('#FFFFFF')
        .onClick(() => this.startOrStop())

      Text('v1 使用倒计时记录屏幕中心点，也支持手动输入 X/Y 坐标。请先打开目标应用并把要点击的位置移动到屏幕中心，再返回开始点击。')
        .fontSize(13)
        .fontColor('#64748B')
        .width('100%')
    }
    .width('100%')
    .height('100%')
    .padding({ left: 24, right: 24, top: 36, bottom: 24 })
    .backgroundColor('#F8FAFC')
  }
}
