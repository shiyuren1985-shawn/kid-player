package family.kidcinema;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Lightweight, bundled vector-style artwork; no network posters or image service. */
public final class ArtView extends View {
    private final Paint paint = new Paint(3);
    private final int theme;
    public ArtView(Context context, int theme) { super(context); this.theme = theme; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void circle(Canvas c, float x, float y, float r, int color) { paint.setColor(color); c.drawCircle(x,y,r,paint); }
    private void shape(Canvas c, int color, float... points) {
        paint.setColor(color); Path p = new Path(); p.moveTo(points[0], points[1]);
        for (int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]); p.close(); c.drawPath(p,paint);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.scale(getWidth()/480f, getHeight()/280f);
        int[] backgrounds = {0xff223b4c,0xffc8d8bc,0xffe3bd90,0xffa7c5c3};
        c.drawColor(backgrounds[Math.floorMod(theme,4)]);
        if (theme % 4 == 0) {
            for (int i=0;i<27;i++) circle(c,(i*97+29)%480,(i*61+17)%280,i%4==0?2.2f:1.2f,0xffe9e4ca);
            circle(c,375,72,51,0xffd3a872); circle(c,359,56,8,0xffbf9762); circle(c,392,88,12,0xffbf9762);
            shape(c,0xffe3b06f,226,190,240,246,254,190);
            shape(c,0xffeab0a0,209,155,189,207,228,191);
            shape(c,0xffeab0a0,268,155,290,207,251,191);
            paint.setColor(0xfff6edda); c.drawOval(210,67,270,201,paint);
            shape(c,0xffeab0a0,212,110,240,62,269,110);
            circle(c,240,136,18,0xff426b7a); circle(c,235,131,8,0xffaac9cc);
        } else if (theme % 4 == 1) {
            circle(c,387,57,30,0xfffbefc9);
            circle(c,95,308,166,0xff698d6b); circle(c,340,335,178,0xff8ca77b);
            for(int i=0;i<3;i++) { float x=100+i*130; shape(c,0xff476850,x,66+i*20,x-50,203,x+50,203); paint.setColor(0xff80694b);c.drawRect(x-5,190,x+5,236,paint); }
        } else if (theme % 4 == 2) {
            circle(c,379,61,31,0xfff9e7b6);
            shape(c,0xffc88d68,0,246,141,69,288,280,0,280);
            shape(c,0xff9e7462,155,280,319,100,480,271,480,280);
            shape(c,0xfff2dcc0,107,112,141,69,179,124,145,113,127,125);
            paint.setColor(0xff607969);c.drawRoundRect(207,155,280,238,19,19,paint);
            paint.setColor(0xffe3bb7a);c.drawRoundRect(220,191,267,227,10,10,paint);
        } else {
            circle(c,384,59,30,0xffe5e6c6);
            for(int i=0;i<4;i++){paint.setColor(i%2==0?0xff779ea4:0xff648992);c.drawOval(-80+i*140,178-i*8,220+i*140,345,paint);}
            paint.setColor(0xfff1d5a9);c.drawOval(137,100,302,176,paint);
            shape(c,0xfff1d5a9,290,137,343,108,343,169);
            circle(c,173,133,5,0xff344e51);
            for(int i=0;i<4;i++) circle(c,90-i*8,102-i*23,4+i,0xffd5e6df);
        }
        c.restore();
    }
}
