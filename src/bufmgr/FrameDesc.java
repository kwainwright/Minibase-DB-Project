package bufmgr;

import global.PageId;

/**
 * This class maintains the relevant state information for a frame.  
 */
public class FrameDesc {
    protected boolean isDirty;
    protected int pinCount;
    protected boolean isValid;
    protected boolean refBit;
    protected PageId pageno; 
   
	 /**
	   * Constructs a FrameDesc by initializing member data. 
	   */
    public FrameDesc () {
    	isDirty = false;
    	pinCount = 0;
    	isValid = false;
    	refBit = false;
    	pageno = null;
    }
}
