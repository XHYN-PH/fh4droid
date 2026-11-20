package studio.deimos.pinyonshift;

public final class PerformancePolicyTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Policy check "+checks);}
    public static void main(String[] args){
        long gib=1024L*1024*1024;
        check(PerformancePolicy.texturePoolMb(4*gib,false)==64);
        check(PerformancePolicy.texturePoolMb(6*gib,false)==128);
        check(PerformancePolicy.texturePoolMb(8*gib,false)==256);
        check(PerformancePolicy.texturePoolMb(12*gib,true)==64);
        check(PerformancePolicy.texturePoolMb(0,false)==256);
        check(PerformancePolicy.shaderWorkers(4*gib,false,8)==1);
        check(PerformancePolicy.shaderWorkers(8*gib,false,4)==1);
        check(PerformancePolicy.shaderWorkers(8*gib,false,8)==2);
        check(PerformancePolicy.configured("-1",128,0,512)==128);
        check(PerformancePolicy.configured("64",256,0,512)==64);
        check(PerformancePolicy.configured("n/a",128,0,512)==128);
        check(PerformancePolicy.configured("100000",128,0,512)==512);
        check(PerformancePolicy.presentationTarget("60","30")==30);
        check(PerformancePolicy.presentationTarget("40","0")==40);
        check(PerformancePolicy.presentationTarget("0","60")==60);
        check(PerformancePolicy.presentationTarget("NaN","Infinity")==0);
        check(PerformancePolicy.seamlessCadence(40,60,new float[]{60,90,120})==120);
        check(PerformancePolicy.seamlessCadence(30,59.94f,new float[]{90,120})==59.94f);
        check(PerformancePolicy.seamlessCadence(40,60,new float[]{60,90})==0);
        check(PerformancePolicy.seamlessCadence(0,120,new float[]{60})==0);
        check(Math.abs(ControllerTools.shaped(.5f,0,1,2)-.25f)<.0001f);
        check(ControllerTools.shaped(-.3f,.4f,.9f,1)==0);
        for(int level:new int[]{0,5,20})check(!PerformancePolicy.trim(level));
        for(int level:new int[]{10,15,40,60,80})check(PerformancePolicy.trim(level));
        System.out.println("PASS: "+checks+" memory, shader, pacing and trim policies");
    }
}
