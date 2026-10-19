package cn.niuma.app;
import java.util.Arrays;
public final class PopupOptionsTest {
    static int checks;
    static void yes(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
    static void order(int mask,int... expected){
        yes(Arrays.equals(expected,PopupOptions.ordered(mask)),"visible order for "+mask);
    }
    public static void main(String[] args){
        yes(PopupOptions.valid(3),"two choices can be saved");
        yes(PopupOptions.valid(7),"three choices can be saved");
        yes(PopupOptions.valid(15),"four choices can be saved");
        for(int mask=0;mask<16;mask++){
            boolean expected=Integer.bitCount(mask)>=2;
            yes(PopupOptions.valid(mask)==expected,"choice count "+mask);
            if(expected)yes(PopupOptions.normalize(mask)==mask,"preserve selection "+mask);
        }
        yes(!PopupOptions.valid(31),"unknown option rejected");
        yes(!PopupOptions.valid(-1),"negative mask rejected");
        yes(PopupOptions.normalize(0)==15,"empty config restored");
        yes(PopupOptions.normalize(1)==15,"single option cannot persist");
        yes(PopupOptions.normalize(31)==15,"invalid config restored");
        order(15,1,2,4,8);
        order(7,1,2,4);
        order(14,2,4,8);
        order(5,1,4);
        order(12,4,8);
        order(9,1,8);
        System.out.println("PASS: "+checks+" popup option checks");
    }
}
