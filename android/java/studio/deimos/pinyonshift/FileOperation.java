package studio.deimos.pinyonshift;

import java.io.InterruptedIOException;

/** Cooperative cancellation at file boundaries and between bounded reads. */
final class FileOperation implements AutoCloseable {
    interface Listener { void progress(String name,long done,long total); }
    private static final ThreadLocal<FileOperation> CURRENT=new ThreadLocal<>();
    private final Listener listener;private volatile boolean cancelled;private volatile Thread thread;private long last;
    FileOperation(Listener listener){this.listener=listener;}
    FileOperation attach()throws InterruptedIOException{thread=Thread.currentThread();CURRENT.set(this);try{check();return this;}catch(InterruptedIOException error){close();throw error;}}
    void cancel(){cancelled=true;Thread worker=thread;if(worker!=null)worker.interrupt();}
    boolean isCancelled(){return cancelled;}
    static String cancelMessage(){return "Cancelled. Completed files are kept; choose the same source to resume.";}
    static void check()throws InterruptedIOException{
        FileOperation op=CURRENT.get();if(Thread.currentThread().isInterrupted()||op!=null&&op.cancelled)throw new InterruptedIOException(cancelMessage());
    }
    static void progress(String name,long done,long total)throws InterruptedIOException{
        check();FileOperation op=CURRENT.get();if(op==null||op.listener==null)return;
        long now=System.nanoTime();if(done!=total&&now-op.last<200000000L)return;op.last=now;op.listener.progress(name,done,total);
    }
    public void close(){if(CURRENT.get()==this)CURRENT.remove();thread=null;Thread.interrupted();}
}
