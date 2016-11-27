package bufmgr;

import global.PageId;

/**
 * <h3>Frame Description Class</h3>
 * This class maintains the relevant information for a frame.  
 */
public class FrameDesc {
    protected boolean dirty;
    protected int pin_count;
    protected boolean valid;
    protected boolean refbit;
    protected PageId pageno; 
   
	 /**
	   * Constructs a FrameDesc by initializing member data. 
	   */
    public FrameDesc () {
    	dirty = false;
    	pin_count = 0;
    	valid = false;
    	refbit = false;
    	pageno = null;
    }
}
