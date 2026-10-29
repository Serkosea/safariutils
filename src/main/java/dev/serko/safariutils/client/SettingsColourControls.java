package dev.serko.safariutils.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
/** Shared HSV/opacity field, geometry and interaction for every settings surface. */
public final class SettingsColourControls {
 private final SettingsColourState state;
 private net.minecraft.client.gui.Font font;private int border,text;private boolean allowsAlpha;
 private int colourFieldLeft,colourFieldTop,colourFieldWidth,colourFieldHeight,hueSliderLeft,hueSliderTop,hueSliderWidth,alphaSliderLeft,alphaSliderTop,alphaSliderWidth;
 public SettingsColourControls(SettingsColourState state){this.state=state;}
	public void draw(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int x, int y, int w, boolean allowsAlpha, int BORDER, int TEXT, int MUTED, int DIM) {
		this.font=font;this.border=BORDER;this.text=TEXT;this.allowsAlpha=allowsAlpha;
		int selected = state.argb();
		drawChecker(graphics, x + 14, y + 54, 72, 40);
		graphics.fill(x + 14, y + 54, x + 86, y + 94, selected);
		outline(graphics, x + 14, y + 54, 72, 40, BORDER);
		String previewLabel = "PREVIEW";
		drawText(graphics, previewLabel,
			x + 14 + (72 - font.width(previewLabel)) / 2, y + 99, MUTED);

		colourFieldLeft = x + 100;
		colourFieldTop = y + 52;
		colourFieldWidth = w - 114;
		colourFieldHeight = 96;
		// One vertical gradient per saturation slice replaces the old grid of over a
		// thousand rectangles while preserving a smooth two-dimensional field.
		int columns = 32;
		for (int column = 0; column < columns; column++) {
			float saturation = column / (float) (columns - 1);
			int x1 = colourFieldLeft + colourFieldWidth * column / columns;
			int x2 = colourFieldLeft + colourFieldWidth * (column + 1) / columns;
			graphics.fillGradient(x1, colourFieldTop, x2, colourFieldTop + colourFieldHeight,
				hsb(255, state.hue(), saturation, 1f), 0xFF000000);
		}
		outline(graphics, colourFieldLeft, colourFieldTop,
			colourFieldWidth, colourFieldHeight, BORDER);
		int selectorX = colourFieldLeft + Math.round(state.saturation() * colourFieldWidth);
		int selectorY = colourFieldTop + Math.round((1f - state.brightness()) * colourFieldHeight);
		graphics.fill(selectorX - 5, selectorY - 5, selectorX + 6, selectorY + 6, BORDER);
		graphics.fill(selectorX - 4, selectorY - 4, selectorX + 5, selectorY + 5, TEXT);
		graphics.fill(selectorX - 2, selectorY - 2, selectorX + 3, selectorY + 3,
			hsb(255, state.hue(), state.saturation(), state.brightness()));

		drawText(graphics, "Hue", x + 14, y + 156, MUTED);
		hueSliderLeft = x + 54;
		hueSliderTop = y + 154;
		hueSliderWidth = w - 68;
		// Two-pixel samples look continuous while keeping the modal inexpensive.
		int hueSegments = Math.max(1, hueSliderWidth / 2);
		for (int i = 0; i < hueSegments; i++) {
			int x1 = hueSliderLeft + hueSliderWidth * i / hueSegments;
			int x2 = hueSliderLeft + hueSliderWidth * (i + 1) / hueSegments;
			graphics.fill(x1, hueSliderTop, x2, hueSliderTop + 12,
				hsb(255, i / (float) hueSegments, 1f, 1f));
		}
		outline(graphics, hueSliderLeft, hueSliderTop, hueSliderWidth, 12, BORDER);
		drawSliderMarker(graphics, hueSliderLeft + Math.round(state.hue() * hueSliderWidth),
			hueSliderTop, 12);
		if (!allowsAlpha) {
			alphaSliderWidth = 0;
			drawText(graphics, "Drag the gradients or enter #RRGGBB", x + 14, y + 184, DIM);
			return;
		}

		drawText(graphics, "Opacity", x + 14, y + 184, MUTED);
		alphaSliderLeft = x + 62;
		alphaSliderTop = y + 182;
		alphaSliderWidth = w - 76;
		drawChecker(graphics, alphaSliderLeft, alphaSliderTop, alphaSliderWidth, 12);
		int rgb = selected & 0xFFFFFF;
		for (int i = 0; i < 36; i++) {
			int x1 = alphaSliderLeft + alphaSliderWidth * i / 36;
			int x2 = alphaSliderLeft + alphaSliderWidth * (i + 1) / 36;
			int alpha = 1 + Math.round(254f * i / 35f);
			graphics.fill(x1, alphaSliderTop, x2, alphaSliderTop + 12, alpha << 24 | rgb);
		}
		outline(graphics, alphaSliderLeft, alphaSliderTop, alphaSliderWidth, 12, BORDER);
		int alphaX = alphaSliderLeft
			+ Math.round((state.alpha() - 1) / 254f * alphaSliderWidth);
		drawSliderMarker(graphics, alphaX, alphaSliderTop, 12);
		drawText(graphics, Math.round(state.alpha() / 255f * 100f) + "%",
			x + w - 42, y + 199, TEXT);
		drawText(graphics, "Drag the gradients or enter #RRGGBB / #AARRGGBB",
			x + 14, y + 214, DIM);
	}
	private static void drawChecker(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		int size = 8;
		for (int row = 0; row * size < h; row++) {
			for (int column = 0; column * size < w; column++) {
				int colour = (row + column & 1) == 0 ? 0xFFB8B8B8 : 0xFF666666;
				graphics.fill(x + column * size, y + row * size,
					Math.min(x + w, x + (column + 1) * size),
					Math.min(y + h, y + (row + 1) * size), colour);
			}
		}
	}

	private void drawSliderMarker(GuiGraphicsExtractor graphics,
			int x, int y, int height) {
		graphics.fill(x - 2, y - 2, x + 3, y + height + 2, border);
		graphics.fill(x - 1, y - 1, x + 2, y + height + 1, text);
	}

 private static int hsb(int a,float h,float s,float b){return SettingsColourState.argb(a,h,s,b);}
 private void drawText(GuiGraphicsExtractor g,String value,int x,int y,int color){SpecialTheme.text(g,font,Component.literal(value),x,y,color);}
 private static void outline(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){g.outline(x,y,w,h,color);}
 private static boolean inside(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
 public boolean update(double x,double y){
  if(inside(x,y,colourFieldLeft,colourFieldTop,colourFieldWidth,colourFieldHeight)){
   state.saturation((float)((x-colourFieldLeft)/colourFieldWidth));state.brightness(1-(float)((y-colourFieldTop)/colourFieldHeight));return true;
  }
  if(inside(x,y,hueSliderLeft,hueSliderTop,hueSliderWidth,12)){state.hue(Math.min(Math.nextDown(1f),(float)((x-hueSliderLeft)/hueSliderWidth)));return true;}
  if(allowsAlpha&&inside(x,y,alphaSliderLeft,alphaSliderTop,alphaSliderWidth,12)){state.alpha(1+Math.round((float)((x-alphaSliderLeft)/alphaSliderWidth)*254));return true;}
  return false;
 }
}
