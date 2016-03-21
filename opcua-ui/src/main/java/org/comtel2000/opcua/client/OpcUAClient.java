package org.comtel2000.opcua.client;

import org.comtel2000.opcua.client.presentation.MainView;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.PersistenceService;
import org.slf4j.LoggerFactory;

import com.airhacks.afterburner.injection.Injector;

import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class OpcUAClient extends Application {

    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(OpcUAClient.class);

    private final DoubleProperty sceneWidthProperty = new SimpleDoubleProperty(1024);
    private final DoubleProperty sceneHeightProperty = new SimpleDoubleProperty(800);

    @Override
    public void start(Stage stage) throws Exception {

	stage.setTitle("OPC-UA Client.FX (" + System.getProperty("javafx.runtime.version") + ")");
	stage.setResizable(true);

	Injector.setLogger((t) -> logger.trace(t));

	PersistenceService session = Injector.instantiateModelOrService(PersistenceService.class);

	Injector.instantiateModelOrService(OpcUaClientConnector.class);

	session.bind(sceneWidthProperty, "scene.width");
	session.bind(sceneHeightProperty, "scene.height");

	MainView main = new MainView();

	final Scene scene = new Scene(main.getView(), sceneWidthProperty.get(), sceneHeightProperty.get());
	stage.setOnCloseRequest((e) -> {
	    sceneWidthProperty.set(scene.getWidth());
	    sceneHeightProperty.set(scene.getHeight());
	    Injector.forgetAll();
	    System.exit(0);
	});
	stage.setScene(scene);
	stage.getIcons().add(new Image(OpcUAClient.class.getResourceAsStream("opc_ua.png")));
	stage.show();

    }

    public static void main(String[] args) {
	launch(args);
    }
}
