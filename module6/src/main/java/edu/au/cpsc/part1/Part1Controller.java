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
  private ImageView secretOverlayImageView;  // Fixed: changed from secretOverlapImageView to match FXML
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
    // 1. Echo binding
    echoTextField.textProperty().bind(messageTextField.textProperty());

    // 2. Bidirectional binding
    firstBidirectionalTextField.textProperty().bindBidirectional(secondBidirectionalTextField.textProperty());

    // 3. Image opacity binding
    secretOverlayImageView.opacityProperty().bind(secretSlider.valueProperty());

    // 4. Checkbox to label binding
    selectMeLabel.textProperty().bind(selectMeCheckBox.selectedProperty().asString());

    // 5. Character count binding
    numberOfCharactersLabel.textProperty().bind(tweetTextField.textProperty().length().asString());

    // 6. Validity binding
    validityLabel.textProperty().bind(
            Bindings.when(tweetTextField.textProperty().length().lessThanOrEqualTo(10))
                    .then("Valid")
                    .otherwise("Invalid")
    );
  }
}