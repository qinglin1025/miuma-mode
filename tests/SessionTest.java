package cn.niuma.app;
public final class SessionTest {
    static int checks;
    static void eq(long expected,long actual,String name) { checks++; if(expected!=actual)throw new AssertionError(name+": expected "+expected+", got "+actual); }
    static void yes(boolean value,String name) { checks++; if(!value)throw new AssertionError(name); }
    public static void main(String[] args) {
        Session s=new Session();
        s.enable(); eq(Session.CHOOSING,s.state(),"enable asks");
        s.startUp(1000);
        eq(38000,s.displayMillis(39000),"elapsed uses monotonic start");
        yes(s.growth(301000)>s.growth(1000),"circle grows");
        yes(s.growth(601000)==1.0f,"ten minute maximum");
        yes(s.growth(901000)==1.0f,"size remains capped");
        eq(900000,s.displayMillis(901000),"time continues after cap");
        s.screenOff(); eq(Session.WAITING,s.state(),"screen off cancels session");
        s.unlock(); eq(Session.CHOOSING,s.state(),"unlock asks again");
        s.startDown(2000,60000);
        eq(60000,s.displayMillis(2000),"countdown initial");
        eq(1,s.displayMillis(61999),"not early");
        yes(!s.expire(61999),"not due");
        yes(s.expire(62000),"due at deadline");
        yes(!s.expire(62001),"expiry once");
        eq(0,s.displayMillis(92000),"countdown cannot go negative");
        s.screenOff(); s.unlock(); s.startDown(100000,60000); s.disable();
        yes(!s.expire(999999),"exit prevents later action"); s.unlock();
        eq(Session.OFF,s.state(),"unlock after exit stays off");
        s.enable(); s.startDown(1000,60000); s.screenOff(); s.unlock();
        yes(!s.expire(999999),"locked countdown cannot expire");
        s.startDown(200000,120000); eq(120000,s.displayMillis(200000),"new session resets");
        s.unlock(); eq(Session.DOWN,s.state(),"duplicate unlock cannot reset running timer");
        boolean invalid=false; try{s.startDown(0,0);}catch(IllegalArgumentException ex){invalid=true;}
        yes(invalid,"invalid duration rejected");
        s.disable(); s.startUp(0); eq(Session.OFF,s.state(),"stopped mode cannot start");

        // Automatic lock finishes the countdown, but USER_PRESENT may not arrive.
        s.enable(); s.startDown(0,60000); s.expire(60000); s.screenOff();
        yes(!s.reconcileScreen(false,false),"sleeping device cannot prompt");
        eq(Session.WAITING,s.state(),"automatic lock waits for actual wake");
        yes(!s.reconcileScreen(true,true),"lit lock screen cannot prompt");
        eq(Session.WAITING,s.state(),"keyguard still waits");
        yes(s.reconcileScreen(true,false),"unlocked screen recovers without USER_PRESENT");
        eq(Session.CHOOSING,s.state(),"recovered screen shows choice");
        yes(!s.reconcileScreen(true,false),"repeated checks do not duplicate choice");
        s.startDown(100000,60000);
        yes(!s.reconcileScreen(true,false),"screen checks preserve active countdown");
        eq(59000,s.displayMillis(101000),"active countdown not restarted");
        s.reconcileScreen(false,false);
        eq(Session.WAITING,s.state(),"screen observation detects missing screen-off event");
        yes(!s.expire(200000),"observed lock cancels deadline");
        yes(s.reconcileScreen(true,false),"next observed unlock asks");
        // A late screen-off delivery must not leave a now-unlocked phone stuck.
        s.screenOff();
        yes(s.reconcileScreen(true,false),"late screen-off recovers on next check");
        s.disable();
        yes(!s.reconcileScreen(true,false),"screen checks cannot restart exited mode");
        eq(Session.OFF,s.state(),"exit survives screen observation");

        System.out.println("PASS: "+checks+" session checks");
    }
}
