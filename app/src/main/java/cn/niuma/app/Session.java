package cn.niuma.app;

/** Pure session state; all times are monotonic milliseconds. */
public final class Session {
    public static final int OFF=0, WAITING=1, CHOOSING=2, UP=3, DOWN=4, DONE=5;
    private int state=OFF;
    private long started, duration;
    public int state(){ return state; }
    public void enable(){ if(state==OFF) state=CHOOSING; }
    public void disable(){ state=OFF; started=duration=0; }
    public void screenOff(){ if(state!=OFF) state=WAITING; started=duration=0; }
    public void unlock(){ if(state==WAITING) state=CHOOSING; }
    /** Recover screen transitions independently of delivery of unlock broadcasts.
     * Returns true only when a waiting session becomes ready to show its choice. */
    public boolean reconcileScreen(boolean interactive, boolean locked){
        if(state==OFF)return false;
        if(!interactive||locked){screenOff();return false;}
        if(state==WAITING){state=CHOOSING;return true;}
        return false;
    }
    public void startUp(long now){ if(state==OFF)return; started=now; duration=0; state=UP; }
    public void startDown(long now,long milliseconds){
        if(milliseconds<=0)throw new IllegalArgumentException("Duration must be positive");
        if(state==OFF)return;
        started=now; duration=milliseconds; state=DOWN;
    }
    public long displayMillis(long now){
        long elapsed=Math.max(0,now-started);
        if(state==UP)return elapsed;
        if(state==DOWN)return Math.max(0,duration-elapsed);
        return 0;
    }
    public float growth(long now){
        return state==UP ? Math.min(1f,Math.max(0,now-started)/600000f) : 0f;
    }
    public float remainingFraction(long now){
        return state==DOWN ? displayMillis(now)/(float)duration : 0f;
    }
    public boolean expire(long now){
        if(state==DOWN && now-started>=duration){ state=DONE; return true; }
        return false;
    }
}
