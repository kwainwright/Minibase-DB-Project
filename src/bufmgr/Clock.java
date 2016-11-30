package bufmgr;

/**
* 
* This class extends the abstract class Replacer, and will be used as the replacement policy
* by the buffer manager class. We override the pickVictim() method in this class with the implementation
* of the clock replacement policy. In the pickVictim() method, we iterate through the frames in the array
* of frame descriptions looking for an available frame to store the current page.
*/
public class Clock extends Replacer {

	private int counter;
    
    public Clock(BufMgr bufmgr)
    {
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
        boolean found = false;
        
        // Go thru all the frame twice if needed
        for (int i=0; i<(frametab.length*2); i++)
        {
            if (!frametab[counter].isValid)
            {
                // Any invalid (no data) frames are chosen first
                found = true;
                break;
            }
            else if (frametab[counter].pinCount == 0)
            {
                // This frame is not pinned
                if (!frametab[counter].refBit)
                {
                    // Found unpinned that has not been referenced
                    // recently
                    found = true;
                    break;
                }
                else
                {
                    // Don't choose this, set reference bit and
                    // keep going
                    frametab[counter].refBit = false;                 
                }
            }
            
            //0, 1, 2 ... 99
            counter = (counter + 1) % frametab.length;
        }
        
        if (found)
        {
            return counter;
        }
        else
        {
            // Could not find a frame, return error
            return -1;
        }
    }
}
