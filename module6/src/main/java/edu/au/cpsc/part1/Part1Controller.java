package edu.au.cpsc.part1;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;

public class Part1Controller {

  @FXML
  private TextField messageTextField, echoTextField, firstBidirectionalTextField, secondBidirectionalTextField;

  @FXML
  private ImageView secretOverlayImageView;

  @FXML
  private Slider secretSlider;

  @FXML
  private CheckBox selectMeCheckBox;

  @FXML
  private Label selectMeLabel;

  @FXML
  private TextField tweetTextField;

  @FXML
  private Label numberOfCharactersLabel, validityLabel;

  public void initialize() {
    echoTextField.textProperty().bind(messageTextField.textProperty());
    firstBidirectionalTextField.textProperty().bindBidirectional(secondBidirectionalTextField.textProperty());
    secretOverlayImageView.opacityProperty().bind(secretSlider.valueProperty());
    selectMeLabel.textProperty().bind(selectMeCheckBox.selectedProperty().asString());
    numberOfCharactersLabel.textProperty().bind(tweetTextField.textProperty().length().asString());

    validityLabel.textProperty().bind(
            Bindings.when(tweetTextField.textProperty().length().lessThanOrEqualTo(10))
                    .then("Valid")
                    .otherwise("Invalid")
    );
  }
}