package bufmgr;

/**
 * 
 * This class extends the abstract class Replacer, and will be used as the
 * replacement policy by the buffer manager class. We override the pickVictim()
 * method in this class with the implementation of the clock replacement policy.
 * In the pickVictim() method, we iterate through the frames in the array of
 * frame descriptions looking for an available frame to store the current page.
 */
public class Clock extends Replacer {

	private int counter;

	public Clock(BufMgr bufmgr) {
		super(bufmgr);
		counter = 0;
	}

	@Override
	public void newPage(FrameDesc fdesc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void freePage(FrameDesc fdesc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void pinPage(FrameDesc fdesc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void unpinPage(FrameDesc fdesc) {
		// TODO Auto-generated method stub

	}

	@Override
	public int pickVictim() {
		// Go around two times if necessary
		for (int i = 0; i < (frametab.length * 2); i++) {
			if (!frametab[counter].isValid) {
				// data in is not valid, choose current
				return counter;
			} else if (frametab[counter].pinCount == 0) {
				// frame is not pinned
				if (frametab[counter].refBit) {
					// set reference bit and continue
					frametab[counter].refBit = false;
				} else {
					// unpinned that has not been referenced recently
					return counter;
				}
			}

			// increment current, mod N
			counter = (counter + 1) % frametab.length;
		}

		// Could not find a frame, return error
		return -1;
	}
}
