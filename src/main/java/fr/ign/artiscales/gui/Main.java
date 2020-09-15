package fr.ign.artiscales.gui;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class Main extends Application {
	// good tuto here : http://tutorials.jenkov.com/javafx/button.html
	// and to make javafx work follow that https://stackoverflow.com/questions/52144931/how-to-add-javafx-runtime-to-eclipse-in-java-11
	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setTitle("Parcel Manager Main Page");

		Label label = new Label("Choose which scale you want to play with !");
		Scene scene = new Scene(label, 400, 200);
		primaryStage.setScene(scene);

		Button buttonProcess = new Button("Process on a single parcel");
		buttonProcess.setText("Process on a single parcel");
		buttonProcess.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

			}
		});

		Button buttonGoal = new Button("Goal on a zone");
		buttonGoal.setText("Goal on a zone");

		Button buttonScenario = new Button("Complex scenario");
		buttonScenario.setText("Complex scenario");

		primaryStage.show();
	}

	public static void main(String[] args) {
		Application.launch(args);
	}
}
