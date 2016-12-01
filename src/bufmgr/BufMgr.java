package bufmgr;

import global.GlobalConst;
import global.Minibase;
import global.Page;
import global.PageId;

/**
 * <h3>Minibase Buffer Manager</h3> The buffer manager manages an array of main
 * memory pages. The array is called the buffer pool, each page is called a
 * frame. It provides the following services:
 * <ol>
 * <li>Pinning and unpinning disk pages to/from frames
 * <li>Allocating and deallocating runs of disk pages and coordinating this with
 * the buffer pool
 * <li>Flushing pages from the buffer pool
 * <li>Getting relevant data
 * </ol>
 * The buffer manager is used by access methods, heap files, and relational
 * operators.
 */
public class BufMgr implements GlobalConst {

	// Page container
	Page[] bufferPool;

	// Frame container
	FrameDesc[] frametab;

	// Create a Page/Frame Mapper that will
	// hold the page to frame relationships
	PageFrameMap pageFrameMap;

	Replacer replacer;

	/**
	 * Constructs a buffer manager by initializing member data.
	 * 
	 * @param numframes
	 *            number of frames in the buffer pool
	 */
	public BufMgr(int numframes) {

		// Initialize containers
		bufferPool = new Page[numframes];
		frametab = new FrameDesc[numframes];
		for (int i = 0; i < numframes; i++) {
			bufferPool[i] = new Page();
			frametab[i] = new FrameDesc();
		}

		replacer = new Clock(this);
		pageFrameMap = new PageFrameMap();

	} // public BufMgr(int numframes)

	/**
	 * The result of this call is that disk page number pageno should reside in
	 * a frame in the buffer pool and have an additional pin assigned to it, and
	 * mempage should refer to the contents of that frame. <br>
	 * <br>
	 * 
	 * If disk page pageno is already in the buffer pool, this simply increments
	 * the pin count. Otherwise, this<br>
	 * 
	 * <pre>
	 * 	uses the replacement policy to select a frame to replace
	 * 	writes the frame's contents to disk if valid and isValid
	 * 	if (contents == PIN_DISKIO)
	 * 		read disk page pageno into chosen frame
	 * 	else (contents == PIN_MEMCPY)
	 * 		copy mempage into chosen frame
	 * 	[omitted from the above is maintenance of the frame table and hash map]
	 * </pre>
	 * 
	 * @param pageno
	 *            identifies the page to pin
	 * @param mempage
	 *            An output parameter referring to the chosen frame. If
	 *            contents==PIN_MEMCPY it is also an input parameter which is
	 *            copied into the chosen frame, see the contents parameter.
	 * @param contents
	 *            Describes how the contents of the frame are determined.<br>
	 *            If PIN_DISKIO, read the page from disk into the frame.<br>
	 *            If PIN_MEMCPY, copy mempage into the frame.<br>
	 *            If PIN_NOOP, copy nothing into the frame - the frame contents
	 *            are irrelevant.<br>
	 *            Note: In the cases of PIN_MEMCPY and PIN_NOOP, disk I/O is
	 *            avoided.
	 * @throws IllegalArgumentException
	 *             if PIN_MEMCPY and the page is pinned.
	 * @throws IllegalStateException
	 *             if all pages are pinned (i.e. pool is full)
	 */
	public void pinPage(PageId pageno, Page mempage, int contents) {

		// See if the page already is mapped into a frame
		Integer frameNumber = pageFrameMap.getFrameFromPage(pageno.pid);
		if (frameNumber == null) {
			// There is no mapping for this page, go find a frame it can
			// live in.
			Integer framenum = replacer.pickVictim();
			if (framenum != -1) {
				// Found a frame for the page to live in
				Page page = bufferPool[framenum];
				FrameDesc frame = frametab[framenum];

				if ((frame.isValid) && (frame.isDirty)) {
					// The frame had a page in it that became dirty,
					// so write it out to the disk before using the frame.
					Minibase.DiskManager.write_page(frame.pageno, page);
					frame.isDirty = false;
				}

				switch (contents) {
				case PIN_DISKIO: {
					// Get the page from disk first, copy it into the frame in
					// the
					// buffer pool, set mempage to refer to it, update the frame
					// descripters and update the hashmap.
					Page diskpage = new Page();
					Minibase.DiskManager.read_page(pageno, diskpage);
					page.copyPage(diskpage);
					mempage.setPage(page);
					frame.pinCount++;
					frame.isValid = true;
					frame.isDirty = false;
					frame.refBit = false;
					frame.pageno = new PageId(pageno.pid);
					pageFrameMap.addToMap(framenum, pageno.pid);
					break;
				}
				case PIN_MEMCPY: {
					// Copy page in mempage into the frame in the buffer pool,
					// set mempage to refer to it, update the frame descripters
					// and update the hashmap.
					page.copyPage(mempage);
					mempage.setPage(page);
					frame.pinCount++;
					frame.isValid = true;
					frame.isDirty = false;
					frame.refBit = false;
					frame.pageno = new PageId(pageno.pid);
					pageFrameMap.addToMap(framenum, pageno.pid);
					break;
				}
				case PIN_NOOP: {
					// This currently does nothing and is not called
					break;
				}
				default: {
					// Received an invalid operation
					throw new IllegalArgumentException();
				}
				}
			} else {
				// Buffer pool is completely full and there are no slots that
				// can be reclaimed. Very bad news.
				throw new IllegalStateException();
			}
		} else {
			// The page is already mapped to a frame. Pin it and set
			// mempage to refer to it.
			frametab[frameNumber].pinCount++;
			mempage.setPage(bufferPool[frameNumber]);
		}

	} // public void pinPage(PageId pageno, Page page, int contents)

	/**
	 * Unpins a disk page from the buffer pool, decreasing its pin count.
	 * 
	 * @param pageno
	 *            identifies the page to unpin
	 * @param dirty
	 *            UNPIN_DIRTY if the page was modified, UNPIN_CLEAN otherwise
	 * @throws IllegalArgumentException
	 *             if the page is not in the buffer pool or not pinned
	 */
	public void unpinPage(PageId pageno, boolean dirty) {

		Integer frameNumber = pageFrameMap.getFrameFromPage(pageno.pid);
		if ((frameNumber == null) || frametab[frameNumber].pinCount == 0) {
			// We were told to unpin a page that was either not in the
			// buffer pool or not even pinned.
			throw new IllegalArgumentException();
		} else {
			FrameDesc frame = frametab[frameNumber];

			if (dirty) {
				// Once your unpinned dirty you stay dirty until your written
				// out to disk the next time the frame is pinned.
				frame.isDirty = dirty;
			}

			// Update the pin count.
			frame.pinCount--;
			if (frame.pinCount == 0) {
				// When all the pins are removed set the reference bit.
				frame.refBit = true;
			}
		}

	} // public void unpinPage(PageId pageno, boolean dirty)

	/**
	 * Allocates a run of new disk pages and pins the first one in the buffer
	 * pool. The pin will be made using PIN_MEMCPY. Watch out for disk page
	 * leaks.
	 * 
	 * @param firstpg
	 *            input and output: holds the contents of the first allocated
	 *            page and refers to the frame where it resides
	 * @param run_size
	 *            input: number of pages to allocate
	 * @return page id of the first allocated page
	 * @throws IllegalArgumentException
	 *             if firstpg is already pinned
	 * @throws IllegalStateException
	 *             if all pages are pinned (i.e. pool exceeded)
	 */
	public PageId newPage(Page firstpg, int run_size) {

		if (getNumUnpinned() == 0) {
			// Buffer pool is already full with unpinned pages
			throw new IllegalStateException();
		} else {
			// Allocate the disk pages, return id of first page
			PageId pageno = Minibase.DiskManager.allocate_page(run_size);

			Integer frameNumber = pageFrameMap.getFrameFromPage(pageno.pid);
			if ((frameNumber != null) && (frametab[frameNumber].pinCount > 0)) {
				// The first page is already mapped into the buffer pool and
				// pinned
				throw new IllegalArgumentException();
			} else {
				// Pin the first page and return its page id
				pinPage(pageno, firstpg, PIN_MEMCPY);
				return pageno;
			}
		}

	} // public PageId newPage(Page firstpg, int run_size)

	/**
	 * Deallocates a single page from disk, freeing it from the pool if needed.
	 * 
	 * @param pageno
	 *            identifies the page to remove
	 * @throws IllegalArgumentException
	 *             if the page is pinned
	 */
	public void freePage(PageId pageno) {

		Integer frameNumber = pageFrameMap.getFrameFromPage(pageno.pid);
		if ((frameNumber != null) && (frametab[frameNumber].pinCount > 0)) {
			// The page is mapped into the buffer pool and pinned,
			// so we can't free it.
			throw new IllegalArgumentException();
		} else {
			// Deallocate the requested disk page
			Minibase.DiskManager.deallocate_page(pageno);
		}

	} // public void freePage(PageId firstid)

	/**
	 * Write all valid and dirty frames to disk. Note flushing involves only
	 * writing, not unpinning or freeing or the like.
	 * 
	 */
	public void flushAllPages() {

		for (FrameDesc frame : frametab) {
			if ((frame.isValid) && (frame.isDirty)) {
				// Only flush frames that have valid pages that are dirty
				flushPage(frame.pageno);
			}
		}

	} // public void flushAllFrames()

	/**
	 * Write a page in the buffer pool to disk, if dirty.
	 * 
	 * @throws IllegalArgumentException
	 *             if the page is not in the buffer pool
	 */
	public void flushPage(PageId pageno) {

		Integer frameNumber = pageFrameMap.getFrameFromPage(pageno.pid);
		if ((frameNumber != null) && (frametab[frameNumber].isDirty)) {
			// Write the page to disk
			Minibase.DiskManager.write_page(pageno, bufferPool[frameNumber]);
			frametab[frameNumber].isDirty = false;
		} else {
			// The page is not even in the buffer pool
			throw new IllegalArgumentException();
		}

	}

	/**
	 * Gets the total number of buffer frames.
	 */
	public int getNumFrames() {
		return frametab.length;
	}

	/**
	 * Gets the total number of unpinned buffer frames.
	 */
	public int getNumUnpinned() {
		int unpinned_count = 0;
		for (FrameDesc frame : frametab) {
			if (frame.pinCount == 0) {
				unpinned_count++;
			}
		}

		return unpinned_count;
	}

} // public class BufMgr implements GlobalConst
