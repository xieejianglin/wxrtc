package org.webrtc;

import android.content.Context;

public interface NetworkChangeDetectorFactory {
  NetworkChangeDetector create(NetworkChangeDetector.Observer paramObserver, Context paramContext);
}
