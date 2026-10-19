package cn.niuma.app;
final class PopupOptions {
 static final int UP=1, DOWN=2, SCREEN_OFF=4, EXIT=8, ALL=15;
 static boolean valid(int mask){return (mask & ~ALL)==0 && Integer.bitCount(mask)>=2;}
 static int normalize(int mask){return valid(mask)?mask:ALL;}
 static int[] ordered(int mask){
  mask=normalize(mask);int[] out=new int[Integer.bitCount(mask)];int i=0;
  for(int bit:new int[]{UP,DOWN,SCREEN_OFF,EXIT})if((mask&bit)!=0)out[i++]=bit;
  return out;
 }
}
