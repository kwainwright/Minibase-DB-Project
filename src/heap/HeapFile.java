package heap;

import global.GlobalConst;
import global.Minibase;
import global.PageId;
import global.RID;

/**
 * <h3>Minibase Heap Files</h3> A heap file is an unordered set of records,
 * stored on a set of pages. This class provides basic support for inserting,
 * selecting, updating, and deleting records. Temporary heap files are used for
 * external sorting and in other relational operators. A sequential scan of a
 * heap file (via the Scan class) is the most basic access method.
 */
public class HeapFile implements GlobalConst {

	static final short DATA_PAGE = 100;
	static final short DIR_PAGE = 200;
	String fileName;
	PageId pageId;
	Boolean isTemp;

	/**
	 * If the given name already denotes a file, this opens it; otherwise, this
	 * creates a new empty file. A null name produces a temporary heap file
	 * which requires no DB entry.
	 */
	public HeapFile(String name) {
		if (name == null) {
			// Construct a temporary heapfile.
			// Set the name to an empty string - need toString() method to work
			isTemp = true;
			fileName = "";
			pageId = null;
		} else {
			// Construct a heapfile.
			// Store the name and get the page ID of the head
			isTemp = false;
			fileName = name;
			pageId = Minibase.DiskManager.get_file_entry(fileName);
		}

		// check to see if the pageID is null. Either this
		// is a temp HeapFile or it's a new one
		if (pageId == null) {
			// Create the head dir and initialize it.
			DirPage dirPage = new DirPage();
			pageId = Minibase.BufferManager.newPage(dirPage, 1);
			dirPage.setCurPage(pageId);
			// unpin it - write it to disk
			Minibase.BufferManager.unpinPage(pageId, UNPIN_DIRTY);
			if (!isTemp) {
				// This is not a temp file, so save the pageId
				// for future retreival
				Minibase.DiskManager.add_file_entry(fileName, pageId);
			}
		}
	}

	/**
	 * Called by the garbage collector when there are no more references to the
	 * object; deletes the heap file if it's temporary.
	 */
	protected void finalize() throws Throwable {
		if (isTemp) {
			deleteFile();
		}
	}

	/**
	 * Deletes the heap file from the database, freeing all of its pages.
	 */
	public void deleteFile() {
		// Start at the head dir.
		PageId dirId = new PageId(pageId.pid);
		DirPage dirPage = new DirPage();

		while (dirId.pid != INVALID_PAGEID) {
			// Pin current dir page and get the next dir page.
			PageId curPageId = new PageId(dirId.pid);
			Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);
			dirId = dirPage.getNextPage();

			// Go thru each directory entry on the dir page.
			for (short i = 0; i < dirPage.getEntryCnt(); i++) {
				// Get the data pageid and free it.
				PageId dataId = dirPage.getPageId(i);
				Minibase.BufferManager.freePage(dataId);
			}

			// Unpin and free the current dir page.
			Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
			Minibase.BufferManager.freePage(curPageId);
		}

		if (!isTemp) {
			// Not temp, so delete the heapfile entry from the disk.
			Minibase.DiskManager.delete_file_entry(fileName);
		}
	}

	/**
	 * Inserts a new record into the file and returns its RID.
	 * 
	 * @throws IllegalArgumentException
	 *             if the record is too large
	 */
	public RID insertRecord(byte[] record) {
		if (record.length > (PAGE_SIZE - DataPage.HEADER_SIZE - DataPage.SLOT_SIZE)) {
			// If the record size is too big then we can't fit it.
			// Max length is 1000 bytes for a record on a data page.
			throw new IllegalArgumentException();
		}

		// Start at the head dir.
		PageId dirId = new PageId(pageId.pid);
		DirPage dirPage = new DirPage();
		PageId curPageId;
		RID rid = null;

		while (dirId.pid != INVALID_PAGEID) {
			// Pin current dir page and get the next dir page.
			curPageId = new PageId(dirId.pid);
			Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);
			dirId = dirPage.getNextPage();

			// Go thru each directory entry on the dir page.
			for (short i = 0; i < dirPage.getEntryCnt(); i++) {
				// We need entry for a data page that can store the record plus
				// the room for the slot.
				if (dirPage.getFreeCnt(i) >= (record.length + DataPage.SLOT_SIZE)) {
					// We found one, so pin the data page and insert the record
					// in it.
					PageId dataId = dirPage.getPageId(i);
					DataPage dataPage = new DataPage();
					Minibase.BufferManager.pinPage(dataId, dataPage, PIN_DISKIO);
					rid = dataPage.insertRecord(record);

					// Record the new record count and new free space count into
					// this
					// directory entry and then unpin the data page.
					dirPage.setRecCnt(i, dataPage.getSlotCount());
					dirPage.setFreeCnt(i, dataPage.getFreeSpace());
					Minibase.BufferManager.unpinPage(dataId, UNPIN_DIRTY);

					// since the data page was found, we can break
					break;
				}
			}

			if (rid != null) {
				// We inserted the record and updated the directory entry so
				// we need to unpin dirty to write changes and get out.
				Minibase.BufferManager.unpinPage(curPageId, UNPIN_DIRTY);
				break;
			}

			// didn't find it, so unpin it clean
			Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
		} // end of the while loop

		if (rid == null) {
			// We never found a page to hold the record, so create a new page
			DataPage dataPage = new DataPage();
			PageId dataId = Minibase.BufferManager.newPage(dataPage, 1);
			dataPage.setCurPage(dataId);
			rid = dataPage.insertRecord(record);
			short slotCount = dataPage.getSlotCount();
			short freeSpace = dataPage.getFreeSpace();
			Minibase.BufferManager.unpinPage(dataId, UNPIN_DIRTY);

			// Need to find a dir page to hold the entry for the new data page
			// We can't just insert the pageId from the previous search, since
			// there may have been deletes on previous dir pages.
			dirId = new PageId(pageId.pid);
			boolean addedEntry = false;

			// this is just to initialize and avoid compilation issues
			curPageId = new PageId(INVALID_PAGEID);

			while (dirId.pid != INVALID_PAGEID) {
				// Pin current dir page and get the next dir page.
				curPageId = new PageId(dirId.pid);
				Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);
				dirId = dirPage.getNextPage();

				short entryCnt = dirPage.getEntryCnt();
				if (entryCnt < DirPage.MAX_ENTRIES) {
					// There's room for an entry on this dir page. So enter it,
					// unpin
					// dir page and get out.
					dirPage.setPageId(entryCnt, dataId);
					dirPage.setRecCnt(entryCnt, slotCount);
					dirPage.setFreeCnt(entryCnt, freeSpace);
					dirPage.setEntryCnt(++entryCnt);
					Minibase.BufferManager.unpinPage(curPageId, UNPIN_DIRTY);
					addedEntry = true;
					break;
				}

				// Still haven't found what we're looking for.
				Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
			}

			if (!addedEntry) {
				// If we got here then every dir page already has max entries.
				// In this
				// case we need to add a new dir page. We already know that
				// curPageId
				// referes to the last dir page from the previous search, so pin
				// it.
				Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);

				// Create the new dir page and record the entry
				DirPage newDirPage = new DirPage();
				PageId newDirId = Minibase.BufferManager.newPage(newDirPage, 1);
				newDirPage.setCurPage(newDirId);
				newDirPage.setPageId(0, dataId);
				newDirPage.setRecCnt(0, slotCount);
				newDirPage.setFreeCnt(0, freeSpace);
				newDirPage.setEntryCnt((short) 1);

				// Set the old last dir page to point to the new last dir page
				// and vice-versa.
				dirPage.setNextPage(newDirId);
				newDirPage.setPrevPage(curPageId);

				// Unpin both dir pages now that they are modified.
				Minibase.BufferManager.unpinPage(newDirId, UNPIN_DIRTY);
				Minibase.BufferManager.unpinPage(curPageId, UNPIN_DIRTY);
			}
		}

		return rid;
	}

	/**
	 * Reads a record from the file, given its id.
	 * 
	 * @throws IllegalArgumentException
	 *             if the rid is invalid
	 */
	public byte[] selectRecord(RID rid) {
		byte[] record;
		DataPage dataPage = new DataPage();
		Minibase.BufferManager.pinPage(rid.pageno, dataPage, PIN_DISKIO);

		try {
			record = dataPage.selectRecord(rid);
		} catch (Exception e) {
			// Invalid rid, so unpin and throw exception.
			Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_CLEAN);
			throw new IllegalArgumentException();
		}

		// Valid rid, so unpin and return the record.
		Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_CLEAN);
		return record;
	}

	/**
	 * Updates the specified record in the heap file.
	 * 
	 * @throws IllegalArgumentException
	 *             if the rid or new record is invalid
	 */
	public void updateRecord(RID rid, byte[] newRecord) {
		// check for null parameters
		if (rid == null || newRecord == null) {
			throw new IllegalArgumentException();
		}

		DataPage page = new DataPage();
		Minibase.BufferManager.pinPage(rid.pageno, page, PIN_DISKIO);
		try {
			page.updateRecord(rid, newRecord);
			Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_DIRTY);
		} catch (IllegalArgumentException exception) {
			// since the record was never updated, unpin cleanly
			Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_CLEAN);
			throw exception;
		}
	}

	/**
	 * Deletes the specified record from the heap file.
	 * 
	 * @throws IllegalArgumentException
	 *             if the rid is invalid
	 */
	public void deleteRecord(RID rid) {
		// check for invalid null rid
		if (rid == null)
			throw new IllegalArgumentException();

		// pin datapage w/record to be deleted
		DataPage dataPage = new DataPage();
		Minibase.BufferManager.pinPage(rid.pageno, dataPage, PIN_DISKIO);

		// get length of record being deleted
		short recordLength = dataPage.getSlotLength(rid.slotno);

		// delete record
		try {
			dataPage.deleteRecord(rid);
			Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_DIRTY);
		} catch (IllegalArgumentException exception) {
			Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_CLEAN);
			throw exception;
		}

		// find dirpages referencing deleted datapage
		DirPage dirPage = new DirPage();
		PageId dirId = new PageId(pageId.pid);

		// go through each dirPage in the heap file
		while (dirId.pid != INVALID_PAGEID) {
			// Pin current dir page and get the next dir page.
			PageId curPageId = new PageId(dirId.pid);
			Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);
			dirId = dirPage.getNextPage();

			// Go thru each directory entry looking for the pageid
			for (int i = 0; i < dirPage.getEntryCnt(); i++) {
				if (dirPage.getPageId(i).pid == rid.pageno.pid) {
					// page id of our record was found, decrement record count
					short newRecCnt = dirPage.getRecCnt(i);
					newRecCnt--;
					dirPage.setRecCnt(i, newRecCnt);

					// update free space count in directory entry
					short newFreeCnt = dirPage.getFreeCnt(i);
					newFreeCnt += recordLength;
					dirPage.setFreeCnt(i, newFreeCnt);

					// check if no records left on datapage (newRecCnt < 1)
					if (newRecCnt < 1) {
						// need to remove empty datapage
						dirPage.compact(i);

						// unpin as dirty to write
						Minibase.BufferManager.unpinPage(rid.pageno, UNPIN_DIRTY);

						// delete empty datapage from memory
						Minibase.BufferManager.freePage(rid.pageno);

						// now check if dirpage is empty
						short newEntryCnt = dirPage.getEntryCnt();

						if (newEntryCnt < 1) {
							// it is empty, check if head
							if (curPageId.pid == pageId.pid) {
								// Unpin the head dir page.
								Minibase.BufferManager.unpinPage(curPageId, UNPIN_DIRTY);
								break;
							} else {
								// not the head dirpage, so delete
								DirPage parentDirPage = new DirPage();
								Minibase.BufferManager.pinPage(dirPage.getPrevPage(), parentDirPage, PIN_DISKIO);

								// set nextpage of parent to nextpage of current
								// dirpage
								parentDirPage.setNextPage(dirPage.getNextPage());

								if (dirPage.getNextPage().pid != INVALID_PAGEID) {
									// pin child dirpage
									DirPage childDirPage = new DirPage();
									Minibase.BufferManager.pinPage(dirPage.getNextPage(), childDirPage, PIN_DISKIO);

									// set prevpage of child to prevpage of
									// current dirpage
									childDirPage.setPrevPage(dirPage.getPrevPage());

									// unpin child page
									Minibase.BufferManager.unpinPage(dirPage.getNextPage(), UNPIN_DIRTY);
								}
								// unpin parent page
								Minibase.BufferManager.unpinPage(dirPage.getPrevPage(), UNPIN_DIRTY);

							}

							// unpin dirty & free
							Minibase.BufferManager.unpinPage(curPageId, UNPIN_DIRTY);
							Minibase.BufferManager.freePage(curPageId);
							break;
						}
					}
				}

			}

			// Unpin the current dir page.
			Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
		}
	}

	/**
	 * Gets the number of records in the file.
	 */
	public int getRecCnt() {
		int count = 0;
		DirPage dirPage = new DirPage();
		PageId dirId = new PageId(pageId.pid);

		// go through each dirPage in the heap file
		while (dirId.pid != INVALID_PAGEID) {
			// Pin current dir page and get the next dir page.
			PageId curPageId = new PageId(dirId.pid);
			Minibase.BufferManager.pinPage(curPageId, dirPage, PIN_DISKIO);
			dirId = dirPage.getNextPage();

			// Go thru each directory entry on the dir page.
			for (short i = 0; i < dirPage.getEntryCnt(); i++) {
				count = count + dirPage.getRecCnt(i);
			}

			// Unpin and free the current dir page.
			Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
		}

		return count;
	}

	/**
	 * Initiates a sequential scan of the heap file.
	 */
	public HeapScan openScan() {
		return new HeapScan(this);
	}

	/**
	 * Returns the name of the heap file.
	 */
	public String toString() {
		return fileName;
	}

} // public class HeapFile implements GlobalConst
