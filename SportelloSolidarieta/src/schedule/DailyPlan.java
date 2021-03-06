package schedule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.sun.org.apache.xalan.internal.xsltc.compiler.sym;

import model.Appointment;
import model.Setting;
import schedule.FreeTimeSlot;
import sun.font.LayoutPathImpl.EndType;

public class DailyPlan 
{
	private int numberOfAppointments; 
	private List<ObservableSlot> dailyPlan = new ArrayList<ObservableSlot>();
	private List<FreeTimeSlot> dailyFreeTime = new ArrayList<FreeTimeSlot>();
	
	// Getters and Setters
	public int getNumberOfAppointments() {
		return numberOfAppointments;
	}

	public void setNumberOfAppointments(int numberOfAppointments) {
		this.numberOfAppointments = numberOfAppointments;
	}
	
	public List<ObservableSlot> getDailyPlan() {
		return dailyPlan;
	}

	public void setDailyPlan(List<ObservableSlot> dailyPlan) {
		this.dailyPlan = dailyPlan;
	}

	public List<FreeTimeSlot> getDailyFreeTimeSlot() {
		return dailyFreeTime;
	}

	public void setDailyFreeTimeSlot(List<FreeTimeSlot> dailyFreeTime) {
		this.dailyFreeTime = dailyFreeTime;
	}
	
	// Constructor for CalendarController
	public DailyPlan (Date date)
	{
		// Initializing the number of daily appointments
		numberOfAppointments = 0;
		
		// Initialize the dailyFreeTime - All freeTime
		Calendar startTime = Calendar.getInstance();
		startTime.setTime(date);				
		Calendar endTime = Calendar.getInstance();
		endTime.setTime(date);
		endTime.add(Calendar.DATE, 1);
		dailyFreeTime.add(new FreeTimeSlot(startTime, endTime));
		
		List<Appointment> dailyAppointments = Appointment.findAppointmentsByDate(date);
	
		// Generating all the slots taken by appointment already in the database 
		createSlotOfAppointments(dailyAppointments);
				
	}
	
	// Constructor for ScheduleController
	public DailyPlan(Date date, Setting settings) 
	{
		// Initializing the number of daily appointments
		numberOfAppointments = 0;
		
		// Setting end day and time for appointment day
		Date start = updateSettingsDateTime(settings.getHStart(), date);
		
		// Setting end day and time for appointment day
		Date end = updateSettingsDateTime(settings.getHEnd(), date);
		
		int appointmentLenghtFromSettings = settings.getAppointmentLength();
		
		// Initialize the dailyFreeTime - All freeTime
		Calendar startTime = Calendar.getInstance();
		startTime.setTime(date);				
		Calendar endTime = Calendar.getInstance();
		endTime.setTime(date);
		endTime.add(Calendar.DATE, 1);
		dailyFreeTime.add(new FreeTimeSlot(startTime, endTime));
		
		List<Appointment> dailyAppointments = Appointment.findAppointmentsByDate(date);
		
		// Initializing current time to start of the working day
		Date currentTime = start;	
		
		// Initializing appointmentLength
		int appointmentLength = appointmentLenghtFromSettings;
					
		// If there are no appointments at all create an all free dailyPlan
		if (dailyAppointments.isEmpty()) 
		{	
			while (currentTime.before(end)) 
			{	
				// Appointment start
				Calendar cal = Calendar.getInstance();
				cal.setTime(currentTime);
				
				// Appointment end
				Calendar appointmentEnd = Calendar.getInstance();
				appointmentEnd.setTime(currentTime);
				appointmentEnd.add(Calendar.MINUTE, appointmentLength);
				
				// End of working day
				Calendar endOfDay = Calendar.getInstance();
				endOfDay.setTime(end);
				
				// Setting the right appointment length at the end of the day
				if (appointmentEnd.after(endOfDay))
				{
					appointmentLength = (int) Duration.between(currentTime.toInstant(),
							end.toInstant()).toMinutes();
				}
				
				Slot currentSlot = new Slot(cal,null,appointmentLength); 
				ObservableSlot observableSlot = new ObservableSlot(currentSlot);
				dailyPlan.add(observableSlot);
				
				currentTime = addMinutesToDate(currentTime, appointmentLength);	
			}
		} 
			else // There are appointments in the day 
		{
		
			// Generating all the slots taken by appointment already in the database 
			createSlotOfAppointments(dailyAppointments);
					
			// Generating other appointments from free time between start - end of day
			while (currentTime.before(end)) 
			{	
				FreeTimeSlot currentFreeTimeSlot = checkSlot(Date.from(currentTime.toInstant()), appointmentLenghtFromSettings);
				if (currentFreeTimeSlot != null)
				{
					// Appointment start
					Calendar appointmentTime = Calendar.getInstance();
					appointmentTime.setTime(Date.from(currentTime.toInstant()));
					
					// Appointment end
					Calendar appointmentEnd = Calendar.getInstance();
					appointmentEnd.setTime(Date.from(currentTime.toInstant()));
					appointmentEnd.add(Calendar.MINUTE, appointmentLenghtFromSettings);
					
					// End of working day
					Calendar endOfDay = Calendar.getInstance();
					endOfDay.setTime(end);
					
					// Setting the right appointment length at the end of the day
					if (appointmentEnd.after(endOfDay))
					{
						appointmentLength = (int) Duration.between(currentFreeTimeSlot.getStartTime().toInstant(),
								end.toInstant()).toMinutes();
					}
					
					Slot currentSlot = new Slot(appointmentTime,null, appointmentLength); 
					
					// Updating the dailyFreeTime
					updateDailyFreeTime(currentSlot, currentFreeTimeSlot);
					
					// Adding the slot to the dailyPlan
					ObservableSlot observableSlot = new ObservableSlot(currentSlot);
					dailyPlan.add(observableSlot);	
				}
				currentTime = addMinutesToDate(currentTime, appointmentLength);	
			}
						
			Calendar startOfDay = Calendar.getInstance();
			startOfDay.setTime(start);
			startOfDay.add(Calendar.SECOND, -1);
	
			Calendar endOfDay = Calendar.getInstance();
			endOfDay.setTime(end);
			endOfDay.add(Calendar.SECOND, +1);
			
			// Filling the gaps of free time with appointments with different length (not default one)
			for (Iterator iterator = dailyFreeTime.iterator(); iterator.hasNext();) 
			{
				FreeTimeSlot currentFreeSlot = (FreeTimeSlot) iterator.next();
				
				int appLength = 0;
				// Managing a free slot beginning before the start time
				if (currentFreeSlot.getStartTime().before(startOfDay) 
						&& currentFreeSlot.getEndTime().after(startOfDay))		
				{
					startOfDay.add(Calendar.SECOND, +1);
					appLength = (int) Duration.between(startOfDay.toInstant(),
						  	currentFreeSlot.getEndTime().toInstant()).toMinutes();
					Slot currentSlot = new Slot(startOfDay,null, appLength);
					
					if (appLength > 0) {						
						//Adding the slot to the dailyPlan
						ObservableSlot observableSlot = new ObservableSlot(currentSlot);
						dailyPlan.add(observableSlot);
					}				
				}
				else if(currentFreeSlot.getStartTime().after(startOfDay) && 
						currentFreeSlot.getEndTime().before(endOfDay)) // Managing all other situations	
				{	
					appLength = (int) Duration.between(currentFreeSlot.getStartTime().toInstant(),
									  	currentFreeSlot.getEndTime().toInstant()).toMinutes();
					Slot currentSlot = new Slot(currentFreeSlot.getStartTime(),null, appLength); 
					
					if (appLength > 0) {						
						//Adding the slot to the dailyPlan
						ObservableSlot observableSlot = new ObservableSlot(currentSlot);
						dailyPlan.add(observableSlot);
					}
				}
				// Managing a free slot ending after the end of the day
				else if (currentFreeSlot.getStartTime().before(endOfDay) && 
						 currentFreeSlot.getEndTime().after(endOfDay))
				{
					endOfDay.add(Calendar.SECOND, -1);
					appLength = (int) Duration.between(currentFreeSlot.getStartTime().toInstant(),
							endOfDay.toInstant()).toMinutes();
					Slot currentSlot = new Slot(currentFreeSlot.getStartTime(),null, appLength);
					
					if (appLength > 0) {						
						//Adding the slot to the dailyPlan
						ObservableSlot observableSlot = new ObservableSlot(currentSlot);
						dailyPlan.add(observableSlot);
					}
					
				}
			}
				
			// Sorting the dailyPlan list
			Collections.sort(dailyPlan, new Comparator<ObservableSlot>() 
			{
		        @Override
		        public int compare(ObservableSlot firstSlot, ObservableSlot secondSlot)
		        {
		            return  firstSlot.getAssociatedSlot().getDateTime().compareTo(secondSlot.getAssociatedSlot().getDateTime());
		        }
		    });
			
			// Checking for duplicate for each slot and remove the clone if found. Need further analysis for proper bug fixing.
			for (int i=0; i<dailyPlan.size(); i++) 
			{
				List<ObservableSlot> allOtherSlotTocheck = new ArrayList<ObservableSlot>();
				allOtherSlotTocheck.addAll(dailyPlan);
				// Remove the appointment to check
				allOtherSlotTocheck.remove(i);
				
				ObservableSlot slotToCheck = dailyPlan.get(i);
				
				for (int j=0; j<allOtherSlotTocheck.size(); j++) 
				{	
					// If there is a clone remove it
					if(slotToCheck.getAssociatedSlot().getDateTime().equals(allOtherSlotTocheck.get(j).getAssociatedSlot().getDateTime()))
					{
						// Remove only a free slot and not a taken appointment
						if(dailyPlan.get(i).getAssociatedSlot().getAssocieatedAppointment() == null)
							dailyPlan.remove(i);
					}			
				} 
			}
			
		}		
	}
	
	// Generating all the slots taken by appointment already in the database 
	private void createSlotOfAppointments(List<Appointment> dailyAppointments) 
	{
		for (Iterator<Appointment> iterator = dailyAppointments.iterator(); iterator.hasNext();) 
		{
			Appointment currentAppointment = iterator.next();
			
			// Only when appointment is not deleted add it to the dailyPlan
			if (!currentAppointment.getFDeleted())
			{
				FreeTimeSlot currentFreeTimeSlot = checkSlot(currentAppointment.getAppointmentDateTime(), currentAppointment.getAppointmentLength());
				if (currentFreeTimeSlot != null)
				{
					Calendar appointmentCal = Calendar.getInstance();
					appointmentCal.setTime(currentAppointment.getAppointmentDateTime());
					Slot currentSlot = new Slot(appointmentCal,currentAppointment,
							currentAppointment.getAppointmentLength()); 
					
					// Updating the dailyFreeTime
					updateDailyFreeTime(currentSlot, currentFreeTimeSlot);
					
					// Adding the slot to the dailyPlan
					ObservableSlot observableSlot = new ObservableSlot(currentSlot);
					dailyPlan.add(observableSlot);
					
					// Updating the number of appointments
					numberOfAppointments++;
				}	
			}
		}	
	}
	
	// Taking the settings time and setting the given day of appointments
	private Date updateSettingsDateTime(Date settingsDate, Date dateForDay) 
	{	
		// Calendar for date
		Calendar calForDay = Calendar.getInstance();
		calForDay.setTime(dateForDay);
		
		// Calendar for time
		Calendar calForTime = Calendar.getInstance();
		calForTime.setTime(settingsDate);
					
		// Updating date fields
		calForTime.set(Calendar.DAY_OF_MONTH, calForDay.get(Calendar.DAY_OF_MONTH));
		calForTime.set(Calendar.MONTH, calForDay.get(Calendar.MONTH));
		calForTime.set(Calendar.YEAR, calForDay.get(Calendar.YEAR));
		
		return calForTime.getTime();	
	}
	
	// Adding minutes to a dateTime
	private Date addMinutesToDate(Date date, int minutes) 
	{	
		// Calendar for date
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
							
		// Updating date fields
		cal.add(Calendar.MINUTE, minutes);
		
		return cal.getTime();			
	}
	
	// Checking if the dailyPlan has room for an appointment
	private FreeTimeSlot checkSlot(Date possibleSlot, int appointmentLength) 
	{	
		Calendar appointmentBegin = Calendar.getInstance();
		appointmentBegin.setTime(possibleSlot);
		appointmentBegin.add(Calendar.SECOND, 1);
		Calendar appointmentEnd = Calendar.getInstance();
		appointmentEnd.setTime(possibleSlot);
		
		// Remove one second because before is a < and not <= 
		appointmentEnd.add(Calendar.MINUTE, appointmentLength);
		appointmentEnd.add(Calendar.SECOND, -1);
 		
		for (Iterator<FreeTimeSlot> iterator = dailyFreeTime.iterator(); iterator.hasNext();) 
		{
			FreeTimeSlot currentTimeSlot = iterator.next();
			
			if (appointmentBegin.after(currentTimeSlot.getStartTime()) && appointmentEnd.before(currentTimeSlot.getEndTime()) )
				return currentTimeSlot;
	
		}

		return null;			
	}
	
	private void updateDailyFreeTime(Slot assignedSlot, FreeTimeSlot slotToModify) 
	{	
		Calendar appointmentStart = Calendar.getInstance();
		appointmentStart.setTime(assignedSlot.getDateTime().getTime());
		
		Calendar appointmentEnd = Calendar.getInstance();
		appointmentEnd.setTime(assignedSlot.getDateTime().getTime());
		appointmentEnd.add(Calendar.MINUTE, assignedSlot.getSlotLength());
		
		// Control if the appointment start is the same of the the start of the slotToModify  
		if (appointmentStart.equals(slotToModify.getStartTime()) && !appointmentEnd.equals(slotToModify.getEndTime())) 
		{
			// Creating the new freeTimeSlot with starting time the end of the appointment  
			FreeTimeSlot newFreeTimeSlot = new FreeTimeSlot(appointmentEnd, slotToModify.getEndTime());
			
			// Replacing in the list
			dailyFreeTime.add(dailyFreeTime.indexOf(slotToModify), newFreeTimeSlot );
			dailyFreeTime.remove(slotToModify);
		}
		else if(appointmentEnd.equals(slotToModify.getEndTime()) && appointmentStart.equals(slotToModify.getStartTime())) // Appointment ends at the end of the freeTimeSlot 
		{
			// Creating the new freeTimeSlot with end time the end of the appointment  
			FreeTimeSlot newFreeTimeSlot = new FreeTimeSlot(slotToModify.getStartTime(), appointmentEnd);
			
			// Replacing in the list
			dailyFreeTime.add(dailyFreeTime.indexOf(slotToModify), newFreeTimeSlot);
			dailyFreeTime.remove(slotToModify);
		}
		else // Appointment in between
		{
			// Creating the freeTimeSlot before the taken slot
			FreeTimeSlot firstFreeTimeSlot = new FreeTimeSlot(slotToModify.getStartTime(), appointmentStart);
			
			// Creating the freeTimeSlot after the taken slot
			FreeTimeSlot secondFreeTimeSlot = new FreeTimeSlot(appointmentEnd, slotToModify.getEndTime());
			
			// Updating the dailyFreeTime
			dailyFreeTime.add(dailyFreeTime.indexOf(slotToModify), firstFreeTimeSlot);
			dailyFreeTime.add(dailyFreeTime.indexOf(slotToModify), secondFreeTimeSlot);
			dailyFreeTime.remove(slotToModify);
			
		}
	}
	
	public ObservableSlot getFirstFreeSlot()
	{
		ObservableSlot firstSlot = null;
		
		for (Iterator iterator = dailyPlan.iterator(); iterator.hasNext();) 
		{
			ObservableSlot currentSlot = (ObservableSlot) iterator.next();
			
			if (currentSlot.getAssociatedSlot().getAssocieatedAppointment() == null) 
			{
				firstSlot = currentSlot;
				break;
			}
		}	
		return firstSlot;
	}
}
