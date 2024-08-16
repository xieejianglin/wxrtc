package org.webrtc;

public class GlRectDrawer extends GlGenericDrawer {
  private static final String FRAGMENT_SHADER = "void main() {\n  gl_FragColor = sample(tc);\n}\n";
  
  private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
    public void onNewShader(GlShader shader) {}
    
    public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight, int viewportWidth, int viewportHeight) {}
  }
  
  public GlRectDrawer() {
    super(FRAGMENT_SHADER, new ShaderCallbacks());
  }
}
