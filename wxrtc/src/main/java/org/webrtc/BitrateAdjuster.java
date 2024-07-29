package org.webrtc;

interface BitrateAdjuster {
  void setTargets(int paramInt, double paramDouble);
  
  void reportEncodedFrame(int paramInt);
  
  int getAdjustedBitrateBps();
  
  double getAdjustedFramerateFps();
}
