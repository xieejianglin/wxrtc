package org.webrtc;

class DynamicBitrateAdjuster extends BaseBitrateAdjuster {
  private static final double BITRATE_ADJUSTMENT_SEC = 3.0D;
  
  private static final double BITRATE_ADJUSTMENT_MAX_SCALE = 4.0D;
  
  private static final int BITRATE_ADJUSTMENT_STEPS = 20;
  
  private static final double BITS_PER_BYTE = 8.0D;
  
  private double deviationBytes;
  
  private double timeSinceLastAdjustmentMs;
  
  private int bitrateAdjustmentScaleExp;
  
  public void setTargets(int targetBitrateBps, double targetFramerateFps) {
    if (this.targetBitrateBps > 0 && targetBitrateBps < this.targetBitrateBps)
      this.deviationBytes = this.deviationBytes * targetBitrateBps / this.targetBitrateBps; 
    super.setTargets(targetBitrateBps, targetFramerateFps);
  }
  
  public void reportEncodedFrame(int size) {
    if (this.targetFramerateFps == 0.0D)
      return; 
    double expectedBytesPerFrame = this.targetBitrateBps / BITS_PER_BYTE / this.targetFramerateFps;
    this.deviationBytes += size - expectedBytesPerFrame;
    this.timeSinceLastAdjustmentMs += 1000.0D / this.targetFramerateFps;
    double deviationThresholdBytes = this.targetBitrateBps / BITS_PER_BYTE;
    double deviationCap = BITRATE_ADJUSTMENT_SEC * deviationThresholdBytes;
    this.deviationBytes = Math.min(this.deviationBytes, deviationCap);
    this.deviationBytes = Math.max(this.deviationBytes, -deviationCap);
    if (this.timeSinceLastAdjustmentMs <= 3000.0D)
      return; 
    if (this.deviationBytes > deviationThresholdBytes) {
      int bitrateAdjustmentInc = (int)(this.deviationBytes / deviationThresholdBytes + 0.5D);
      this.bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
      this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -BITRATE_ADJUSTMENT_STEPS);
      this.deviationBytes = deviationThresholdBytes;
    } else if (this.deviationBytes < -deviationThresholdBytes) {
      int bitrateAdjustmentInc = (int)(-this.deviationBytes / deviationThresholdBytes + 0.5D);
      this.bitrateAdjustmentScaleExp += bitrateAdjustmentInc;
      this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, BITRATE_ADJUSTMENT_STEPS);
      this.deviationBytes = -deviationThresholdBytes;
    } 
    this.timeSinceLastAdjustmentMs = 0.0D;
  }
  
  private double getBitrateAdjustmentScale() {
    return Math.pow(BITRATE_ADJUSTMENT_MAX_SCALE, this.bitrateAdjustmentScaleExp / BITRATE_ADJUSTMENT_STEPS);
  }
  
  public int getAdjustedBitrateBps() {
    return (int)(this.targetBitrateBps * getBitrateAdjustmentScale());
  }
}
