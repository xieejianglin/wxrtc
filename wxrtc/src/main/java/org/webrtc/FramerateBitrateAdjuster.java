package org.webrtc;

class FramerateBitrateAdjuster extends BaseBitrateAdjuster {
  private static final int DEFAULT_FRAMERATE_FPS = 30;
  
  public void setTargets(int targetBitrateBps, double targetFramerateFps) {
    this.targetFramerateFps = DEFAULT_FRAMERATE_FPS;
    this.targetBitrateBps = (int)((targetBitrateBps * DEFAULT_FRAMERATE_FPS) / targetFramerateFps);
  }
}
