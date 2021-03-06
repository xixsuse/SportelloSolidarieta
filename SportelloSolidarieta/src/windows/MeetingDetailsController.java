package windows;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DateCell;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.stage.Stage;
import javafx.util.Callback;
import utilities.Formatter;

import java.math.BigDecimal;
import java.time.LocalDate;

import application.MainCallback;
import application.PageCallback;
import dal.DbUtil;

public class MeetingDetailsController {

	/*
	 * MEMBERS
	 */

	private MainCallback main; // Interface to callback the main class
	private PageCallback previousPage;

	/*
	 * JAVAFX COMPONENTS
	 */

    @FXML
    private Button button_save;
    
	@FXML
	private TextField value;

	@FXML
	private TextArea descriptionText;

	@FXML
	private DatePicker date;

	/*
	 * CONSTRUCTOR
	 */

	public MeetingDetailsController(MainCallback main, PageCallback currentPage) {
		this.main = main;
		previousPage = currentPage;
	}

	/*
	 * SCENE INITIALIZATION
	 */

	@FXML
	private void initialize() {
		// disable dates in the future for the date pickers
		final Callback<DatePicker, DateCell> dayCellFactory = new Callback<DatePicker, DateCell>() {
			@Override
			public DateCell call(final DatePicker datePicker) {
				return new DateCell() {
					@Override
					public void updateItem(LocalDate item, boolean empty) {
						super.updateItem(item, empty);

						if (item.isAfter(LocalDate.now())) {
							setDisable(true);
						}
					}
				};
			}
		};
		date.setDayCellFactory(dayCellFactory);

		// binding the meeting to layout
		descriptionText.setText(main.getSelectedMeeting().getDescription());
		value.setText(Formatter.formatNumber(main.getSelectedMeeting().getAmount().toString()));
		date.setValue(main.getSelectedMeeting().getDate());
		
		// disable editing if  
		if (main.getSelectedAssisted().getIsRefused() || main.getSelectedAssisted().getIsReunitedWithFamily())
		{
			button_save.setDisable(true);
		}
	}

	/*
	 * JAVAFX ACTIONS
	 */

	@FXML
	void saveMeeting(ActionEvent event) {

		int meetingIndex = main.getSelectedAssisted().getMeetings().indexOf(main.getSelectedMeeting());

		main.getSelectedMeeting().setDate(date.getValue());
		if (descriptionText.getText().length() <= 1000) {
			main.getSelectedMeeting().setDescription(descriptionText.getText());
			try {
				String valueToSave = Formatter.reverseFormatNumber(value.getText());
				// Check for two digits after comma
				if (valueToSave.indexOf(".") == -1 || valueToSave.substring(valueToSave.indexOf(".") + 1).length() <= 2) {
					main.getSelectedMeeting().setAmount(new BigDecimal(valueToSave).setScale(2));
				} else {
					throw new IllegalArgumentException();
				}

				main.setSelectedMeeting(DbUtil.saveMeeting(main.getSelectedMeeting()));
				showAlertAddedMeetingToAssistedDetail();

				switch (main.getRequestedOperation()) {
				case CREATE:
					main.getSelectedAssisted().getMeetings().add(main.getSelectedMeeting());
					break;

				case UPDATE:
					main.getSelectedAssisted().getMeetings().set(meetingIndex, main.getSelectedMeeting());
					break;
				}
				previousPage.refresh();
				main.setSelectedMeeting(null);
			} catch (NumberFormatException e) {
				String message = "Inserire un numero valido";
				showAlertValueError(message);
			} catch (IllegalArgumentException e) {
				String message = "Numero massimo di decimali: 2";
				showAlertValueError(message);
			}
		} else {
			showAlertMaxCharacterError();
		}
	}

	@FXML
	void toAssistedDetails(ActionEvent event) {
		previousPage.refresh();
		Stage stage = (Stage) descriptionText.getParent().getScene().getWindow();
		stage.close();
	}

	/*
	 * OTHER METHODS
	 */

	// Alerts

	// Meeting added to assisted detail
	private void showAlertAddedMeetingToAssistedDetail() {
		Alert alert = new Alert(AlertType.INFORMATION);
		alert.setTitle("Messaggio di conferma");
		alert.setHeaderText("Incontro aggiunto correttamente");
		alert.setContentText("Ritorno ai dettagli dell'assistito");
		alert.setOnCloseRequest(new EventHandler<DialogEvent>() {
			@Override
			public void handle(DialogEvent event) {
				Stage stage = (Stage) descriptionText.getParent().getScene().getWindow();
				stage.close();
			}
		});
		alert.showAndWait();
	}

	// Error alert for too many character
	private void showAlertMaxCharacterError() {
		Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Messaggio di errore");
		alert.setHeaderText("Superato il limite massimo di caratteri per la descrizione");
		alert.setContentText("Numero massimo di caratteri: 1000");
		alert.showAndWait();
	}

	// Error alert for invalid value
	private void showAlertValueError(String message) {
		Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Messaggio di errore");
		alert.setHeaderText("Valore elemosina non corretto");
		alert.setContentText(message);
		alert.showAndWait();
	}

}
