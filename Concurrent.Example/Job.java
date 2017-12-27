/**
 * @fileName Job.java
 * @date August 12, 2017
 * @author Jonathan Pearson
 * @purpose The Job class defines a job field requirement at a Sea Port. For
 *          each job read into the constructor, it defines a JPanel with the job
 *          components and determines the parameters of the job for the ship. 
 *          There are enumeration variables defined to show state of progress for 
 *          each job buttons. This includes jobs that are waiting in queue, jobs 
 *          paused, jobs running, jobs that are completed. Each enumeration 
 *          variable is defined by a different color for easy recognition. The 
 *          toggleGoFlag() and toggleNoGoFlag() method display and partially 
 *          control some of the job progression status. The run() method uses 
 *          synchronized blocks to keep track of jobs of the sea port. A toString()
 *          method will define the String representation of the Job class when 
 *          called. The method checkRequirements() will check whether the port
 *          has skills needed for a job to execute and if not, the job will be
 *          canceled by calling the cancelJob() method to set flags to stop the
 *          thread from progressing.  The hasJobRequirements() will check when
 *          the job thread is in waiting to see if the resources are available.
 *          When available the thread will progress and use the getNextShip(),
 *          assignNextShip(), and assignNextDock() methods to remove the current
 *          job thread, remove the ship from dock, and add a new ship to the 
 *          dock.  The getJobAsPanel() defines a way to let the job progression 
 *          be displayed in the GUI.  The setSkillText() will display resources
 *          still needed by the job before execution on the GUI.  updateLoggerText()
 *          sets and updates the log list of canceled jobs to the GUI.
 *
 */
// import class packages
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

public class Job extends Thing implements Runnable {

    // define class field variables
    double duration;
    ArrayList<String> requirements = new ArrayList<>();
    ArrayList<String> tempList;
    ConcurrentHashMap<Integer, String> neededResources = new ConcurrentHashMap<>();

    static Random rn = new Random(); // to randomly pick next ship from queue

    private Ship worker = null;
    private Dock dock = null;
    private SeaPort port = null;
    double jobTime;
    JProgressBar bar = new JProgressBar();
    boolean goFlag = true, noGoFlag = true;
    private volatile boolean stop = false;
    JButton pauseBtn = new JButton("Pause");
    JButton stopBtn = new JButton("Stop");
    Status status = Status.SUSPENDED;

    private final HashMap<Integer, SeaPort> spMap;
    private final HashMap<Integer, Dock> dockMap;
    private final HashMap<Integer, Person> personReq;
    private final BlockingQueue<String> reqText;
    JPanel updatePanel;
    JTextArea text;

    /**
     * declare the status states
     */
    enum Status {
        RUNNING, SUSPENDED, WAITING, COMPLETE, CANCELLED
    };

    /**
     * Default constructor defines the worker ship and the job of the worker.
     *
     * @param sc scanner to define job parameters
     * @param getShip ship HashMap
     * @param getDock dock HashMap
     * @param runPanel GUI JPanel for each job read in
     * @param spMap sea port HashMap
     * @param personMap
     */
    public Job(Scanner sc, HashMap<Integer, Ship> getShip,
            HashMap<Integer, Dock> getDock, JPanel runPanel,
            HashMap<Integer, SeaPort> spMap,
            HashMap<Integer, Person> personMap) {
        super(sc);

        this.spMap = spMap;
        this.dockMap = getDock;
        this.updatePanel = runPanel;

        // define parent index
        int target = parent;
        // define worker ship of job
        worker = (Ship) (getShip.get(target));

        // if the ship has a dock, assign a dock and port
        if (getDock.get(worker.parent) != null) {
            dock = (Dock) (getDock.get(worker.parent));
            port = (SeaPort) (spMap.get(dock.parent));
        } else {
            dock = new Dock();
            port = (SeaPort) (spMap.get(worker.parent));
        }

        // define job duration time
        jobTime = sc.nextDouble();

        // define job needs
        String skillNeed;
        this.personReq = personMap;
        this.tempList = new ArrayList<>();
        this.reqText = new LinkedBlockingQueue<>();

        // read in requirements for each job
        while (sc.hasNext()) {
            skillNeed = sc.next();
            requirements.add(skillNeed);
            reqText.add(skillNeed);
        }

        // get the defined JPanel
        getJobAsPanel(runPanel);
    } // end constructor

    /**
     * toggleGoFlag() method defines the button state that controls job
     * progression
     */
    public void toggleGoFlag() {
        // toggle flag state
        goFlag = !goFlag;

        if (goFlag == false) {
            pauseBtn.setText("Continue");
        } else {
            pauseBtn.setText("Pause");
        }
    }// end toggleGoFlag() method

    /**
     * toggelNoGoFlag() defines the state of progression button
     */
    public void toggleNoGoFlag() {
        noGoFlag = false;
        stopBtn.setBackground(Color.RED);
    }// end toggleGoFlag() method

    /**
     * showStatus() defines how to display the state of progression button
     *
     * @param st enumeration defined
     */
    private void showStatus(Status st) {
        status = st;

        switch (status) {
            case RUNNING:
                stopBtn.setBackground(Color.GREEN);
                stopBtn.setText("Running");
                break;
            case SUSPENDED:
                stopBtn.setBackground(Color.YELLOW);
                stopBtn.setText("Suspended");
                break;
            case WAITING:
                stopBtn.setBackground(Color.ORANGE);
                stopBtn.setText("Waiting");
                break;
            case COMPLETE:
                stopBtn.setBackground(Color.RED);
                stopBtn.setText("Complete");
                break;
            case CANCELLED:
                stopBtn.setBackground(Color.LIGHT_GRAY);
                stopBtn.setText("JOB CANCELLED");
                break;
        } // end switch
    } // end showStatus() method

    /**
     * run() method defines the job progress and synchronization of the Threads
     */
    @Override
    public void run() {
        // defien temp to hold parent index
        int temp;
        // set text of job resources GUI
        text.setText(setSkillText());

        // define the start time and find the difference with the 
        // job time given to determine total duration of the job
        long time = System.currentTimeMillis();
        long startTime = time;
        long stopTime = (long) (time + 1000 * jobTime);
        duration = stopTime - time;

        // used synchronized blocks for Threads
        synchronized (port) {
            // while the busyFlag is true show status WAITING
            while (worker.busyFlag || !hasJobRequirements()) {

                showStatus(Status.WAITING);

                // check if job was cancelled
                if (stop) {
                    showStatus(Status.CANCELLED);
                    // set busyFlag false
                    worker.busyFlag = false;
                    // set action to remove job and get next job or ship
                    if (worker.jobs.isEmpty() && !port.que.isEmpty()) {
                        temp = worker.parent;
                        getNextShip(temp);
                        port.notify();
                    } else {
                        // remove job when complete
                        worker.jobs.remove(this);
                    }
                }
                try {
                    // wait on the port to free a lock
                    port.wait();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                    return;
                } // end try/catch block
            } // end while waiting for worker to be free

            // start next job
            worker.busyFlag = true;
            worker.setJob(this);
            port.getResources(requirements, neededResources, this, reqText);
            showStatus(Status.RUNNING);
        } // end synchroniced on worker

        // run the next job while conditions are true
        while (time < stopTime && noGoFlag) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            if (goFlag) {
                showStatus(Status.RUNNING);
                time += 100;
                bar.setValue((int) (((time - startTime) / duration) * 100));
            } else {
                showStatus(Status.SUSPENDED);
            } // end if stepping
        } // end running

        // update progress when job is finished
        bar.setValue(100);
        showStatus(Status.COMPLETE);
        noGoFlag = false;
        worker.setJob(null);
        pauseBtn.setEnabled(false);
        port.releaseResources(requirements, neededResources, this);

        // synchronized block to relase lock
        synchronized (port) {
            // set flag to not busy
            worker.busyFlag = false;
            // remove job when complete
            worker.jobs.remove(this);

            // define temp variable for the parent
            temp = worker.parent;

            // when job ArrayList is empty
            if (worker.jobs.isEmpty()) {

                // if que has ships, add ship to open dock
                if (!port.que.isEmpty()) {
                    // get next ship
                    getNextShip(temp);
                } else if (port.que.isEmpty()) {
                    // if queue is empty remove remaining ship at dock
                    assignNextDock(temp);
                }
            }
            // notify all threads
            port.notifyAll();
        }
    } // end run method

    /**
     * checkRequirements() will compare job requirements ArrayList to persons
     * with skills at sea port
     */
    public void checkRequirements() {
        if (!requirements.containsAll(personReq.values())) {
            cancelJob();
        }
    }

    /**
     * hasJobRequirements() will check if the resources are available for use 
     * by the job at the sea port.  If resources are not available, a false 
     * flag will be returned.
     * 
     * @return 
     */
    public synchronized boolean hasJobRequirements() {
        // set skills needed for job in job GUI panel
        setSkillText();

        // if no more requirements or none exist, proceed with job
        if (requirements.isEmpty()) {
            return true;
        }
        if (port.resources.containsAll(requirements)) {
            return true;
        } else {
	   // check requirements needed
           checkRequirements();
	}
        return false;
    }

    /**
     * cancelJob() sets flags that will stop the job and get the next job.
     * It also will update the log file with the cancelled job
     */
    public void cancelJob() {
        stop = true;
        noGoFlag = false;
        updateLoggerText();
    }

    /**
     * assignNextShip() sets the ship at the dock to the next available ship
     * at random from the queue
     */
    public void assignNextShip() {
        // remove any ship from que
        int i = rn.nextInt(port.que.size());
        // add ship from queue to dock
        dock.ship = port.que.remove(i);
    }

    /**
     * assignNextDock() removes the ship from the dock to make it available
     * for the next ship
     * 
     * @param temp defines the ship parents index
     */
    public void assignNextDock(int temp) {
        this.dock = dockMap.get(temp);
        dock.ship = null;
    }

    /**
     * getNextShip() method removes a ship from a dock and assigns the next
     * ship by using logic to determine if more jobs are active or empty.  It
     * will assign the next ship and set the busy flag to not busy
     * 
     * @param temp defines the ship parents index
     */
    public synchronized void getNextShip(int temp) {
        // assign new Dock
        assignNextDock(temp);
        // assign new index and get new ship
        assignNextShip();

        // check if new ship has jobs, if no jobs found
        // assign a new ship to the dock
        while (!port.que.isEmpty() && dock.ship.jobs.isEmpty()) {
            // assign new index and get new ship
            assignNextShip();
        }
        // define list index of the ship
        int j = port.ships.indexOf(dock.ship);

        // reassign ship to sea port at dock
        spMap.get(dock.parent).ships.set(j, dock.ship);

        worker = dock.ship;
        worker.parent = temp;
        dock.ship.parent = temp;

        dock.ship.busyFlag = false;
    }

    /**
     * getJobAsPanel() method defines the JPanel that contains the job
     * progression for each job. Many of these JPanels will be nested in the Job
     * tab pane for each job defined
     *
     * @param jrun the JPanel defined for each job
     */
    public void getJobAsPanel(JPanel jrun) {
        JPanel panel = jrun;
        text = new JTextArea();

        // define new progress bar
        bar = new JProgressBar();
        bar.setStringPainted(true);

        // define panel
        text.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        text.setFont(new java.awt.Font("MonoSpaced", java.awt.Font.PLAIN, 10));
        text.setText(setSkillText());
        panel.add(bar);
        panel.add(new JLabel(worker.name, SwingConstants.CENTER));
        panel.add(new JLabel(name, SwingConstants.CENTER));
        panel.add(text);
        panel.add(stopBtn);
        panel.add(pauseBtn);

        // pauseBtn to pause job progress
        pauseBtn.addActionListener((ActionEvent e) -> {
            toggleGoFlag();
        });
        // stopBtn shows status of job which will cancel job when clicked
        stopBtn.addActionListener((ActionEvent e) -> {
            toggleNoGoFlag();
        });
    }

    /**
     * setSkillTest() sets the job requirements to be listed in the job panel
     * for each job and removed when the job has all of it's resources
     * 
     * @return 
     */
    public String setSkillText() {
        String st;

        if (requirements.isEmpty() || reqText.isEmpty()) {
            st = "";
        } else {
            st = reqText.toString();
        }

        return st;
    }

    /**
     * updateLoggerText() sets the log text when a job is canceled
     */
    public void updateLoggerText() {
        port.logger += this.name + " cancelled for  --> " + worker.name + "\n";
        port.loggerPane.setText(port.logger);
    }

    /**
     * The toString() method returns a String representation of the Job class.
     *
     * @return
     */
    @Override
    public String toString() {
        String st = String.format("job: %7d: %15s: %7d: %5f",
                index, name, worker.index, jobTime);

        return st;
    } // end toString() method
}
