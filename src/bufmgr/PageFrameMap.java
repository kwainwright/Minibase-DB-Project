package bufmgr;

import java.util.HashMap;

/**
 * <h3>Page Frame Mapper</h3>
 * This class maintains the relationships between pages and frames.
 * It has two maps to facilitate quick lookups in either direction.  
 * It provides the following services:
 * <ol>
 * <li>Adding pages and frames to the hash maps
 * <li>Getting a frame given a page
 * <li>Getting a page given a frame
 * <li>Removing a page/frame relationship
 * </ol>
 * This class is utilized by the BufMgr for quick look ups.
 */
public class PageFrameMap {
	
	//Create HashMap variables to hold the page to frame relationships
	HashMap<Integer, Integer> pageToFrame;
	HashMap<Integer, Integer> frameToPage;

	 /**
	   * Constructs a PageFrameMap by initializing member data. 
	   */
	public PageFrameMap () {
		pageToFrame = new HashMap<>();
		frameToPage = new HashMap<>();
	}
	
	 /**
	   * Adds a given frame/page relationship to the maps.
	   * 
	   * @param frame frame to be added
	   * @param page page to be added 
	   */
	public void addToMap(int frame, int page) {
		Integer oldPage = frameToPage.get(frame);
		if (oldPage != null)
		{
			// This frame was mapped to a previous page
			// so remove old entries from both hashmaps. 
			pageToFrame.remove(oldPage);
			frameToPage.remove(frame);
		}

		// Update (add to) both hashmaps
		pageToFrame.put(page, frame);
		frameToPage.put(frame, page);
	}
	
	 /**
	   * Gets the frame for a given page
	   * 
	   * @param page page to be used in the search 
	   * @return frame frame associated with the input page
	   */
	public Integer getFrameFromPage (int page) {
		return pageToFrame.get(page);
	}
	
	 /**
	   * Gets the page for a given frame
	   * 
	   * @param frame frame to be used in the search 
	   * @return page page associated with the input frame
	   */
	public Integer getPageFromFrame (int frame) {
		return frameToPage.get(frame);
	}
	
	 /**
	   * Removes a given frame/page relationship from the maps.
	   * 
	   * @param frame frame to be removed
	   * @param page page to be removed 
	   */
	public void removeFromMap(int frame, int page) {
		// This frame was mapped to a previous page
		// so remove old entries from both hashmaps. 
		pageToFrame.remove(page);
		frameToPage.remove(frame);
	}
}
