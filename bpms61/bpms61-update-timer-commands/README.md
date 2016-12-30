Skip a process stopped on a timer in BPM Suite 6.1
--
This project has two commands to skip a timer on BPM Suite 6.1 only, when the executor API was public.

To use it you must:
* Build this project using `mvn clean package`
    - Type: **example.executor_update_timer.commands.SetTimerAsTriggeredCommand** to mark a timer as triggered and make the process continue the execution after the timer or **example.executor_update_timer.commands.CancelTimerCommand** to simply cancel a timer node;
Then the command should run and cancel/trigger the timer. Make sure the provided process instance ID is stopped on a timer or 