/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

/**
 * import the following elements
 */
import React, {useRef, useEffect} from 'react';
import type {PropsWithChildren} from 'react';
import { WebView } from 'react-native-webview';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  Button,
  View,
  NativeModules,
  NativeEventEmitter,
} from 'react-native';
const {PrinterListModule} = NativeModules;
/**
 * end - import
 */

/**
 * the following elements not mandatory, only for this experimental project
 */
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import PrinterListView from './PrinterListView.js';
import SampleList from './SampleList.js';
import {
  Colors,
  DebugInstructions,
  Header,
  LearnMoreLinks,
  ReloadInstructions,
} from 'react-native/Libraries/NewAppScreen';
/**
 * end - not mandatory
 */

type SectionProps = PropsWithChildren<{
  title: string;
}>;

function Section({children, title}: SectionProps): JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  return (
    <View style={styles.sectionContainer}>
      <Text
        style={[
          styles.sectionTitle,
          {
            color: isDarkMode ? Colors.white : Colors.black,
          },
        ]}>
        {title}
      </Text>
      <Text
        style={[
          styles.sectionDescription,
          {
            color: isDarkMode ? Colors.light : Colors.dark,
          },
        ]}>
        {children}
      </Text>
    </View>
  );
}
const Stack = createNativeStackNavigator();

/**
 * this is mock html to test the functions, pay attention at the javascript consts:
 * 1. sendDataToReactNativeApp -> to send data from webview to RN. it will send data to RN for print purpose via postMessage, the format sent is : <printer_name>|<text_to_print>. this will be catched by Homescreen.onMessage function.
 * 2. refreshPrinters -> to refresh the printers list in dropdown and the list below. it will send "REFRESH_PRINTERS" via postMessage, and refresh list handled from Homescreen.onMessage function.
 * there's also an event listener:
 * we use this event listener to get data from RN to webview. on this example we get the printer names from native module function and add it to the dropdown list and text list
 */
const customHTML = `
     <body>

     <h1>Test print text</h1>
     <p>Enter your text:</p>
     <textarea id="freeform" name="freeform" rows="4" cols="50">
     Enter text here...
     </textarea>
     <br/>
     <label>Select Printer:</label>
     <select name="Printers" id="printers">
     </select>
     <br/>
     <button onclick="sendDataToReactNativeApp()" type="button">Print</button>
     <br/>
     <br/>
     <br/>
     <button onclick="refreshPrinters()" type="button">Refresh Printers</button>
     <br/>
     <label id="lblListPrinter">Printers:<br/></label>
     <script>
       function removeOptions(selectElement) {
          var i, L = selectElement.options.length - 1;
          for(i = L; i >= 0; i--) {
             selectElement.remove(i);
          }
       }
       const sendDataToReactNativeApp = async () => {
            var el = document.getElementById('printers');
            var selectedPrinter = el.options[el.selectedIndex].innerHTML;
            window.ReactNativeWebView.postMessage(selectedPrinter+'|'+document.getElementById("freeform").value);
       };
       const refreshPrinters = async () => {
            document.getElementById('lblListPrinter').innerHTML = 'Printers:<br/>';
            removeOptions(document.getElementById('printers'));
            window.ReactNativeWebView.postMessage("REFRESH_PRINTERS");
       };
       document.addEventListener("message", function(data) {
            document.getElementById('lblListPrinter').innerHTML += data.data;
            document.getElementById('lblListPrinter').innerHTML += '<br/>';

            var select = document.getElementById('printers');
            var opt = document.createElement('option');
            opt.value = 'printer'+select.options.length;
            opt.innerHTML = data.data;
            select.appendChild(opt);
       });
     </script>
     </body>
     `;

function App(): JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
  };

  return (
    <NavigationContainer>
        <Stack.Navigator>
          <Stack.Screen
            name="Home"
            component={HomeScreen}
          />
          <Stack.Screen name="Printer2" component={PrinterScreen2} />
          <Stack.Screen name="Sample" component={SampleListScreen} />
        </Stack.Navigator>
    </NavigationContainer>

  );
}

const HomeScreen = ({navigation}) => {
    // we use this to process data from webview, if its "REFRESH_PRINTERS" then we call refreshPrinters, else means we call print and split the data by "|" as printerName & print text
    function onMessage(data) {
        if(data.nativeEvent.data=="REFRESH_PRINTERS"){
            refreshPrinters();
        }
        else{
            const words  = data.nativeEvent.data.split('|');
            PrinterListModule.print(words[0],words[1]);
        }
    }

    //call getListOfPrinters
    function refreshPrinters(){
        PrinterListModule.getListOfPrinters()
    }

    //register this ref on the webview
    const webviewRef = useRef();

    //we use this to send the data to webview
    function sendDataToWebView(data) {
        console.log(`send to webview ${data}`);
        this.webviewRef.postMessage(data);
      }

    //register the event emitter in useEffect section
    useEffect(() => {
        //register the emitter
        const eventEmitter = new NativeEventEmitter(PrinterListModule);
        //make sure we use the same event name 'PrinterEvent' like in the native module code
        let eventListener = eventEmitter.addListener('PrinterEvent', event => {
          console.log(event.printerName);
          //when the printer data is received from native module, we forward it to the webview to show it
          sendDataToWebView(event.printerName); //to get the printerName
        });

        // Removes the listener once unmounted
        return () => {
          eventListener.remove();
        };
      }, []);

  return (
    <SafeAreaView style={{ flex: 1 }}>
        <Button
              title="Sample List Screen!"
              color="#848484"
              onPress={() =>
                 navigation.navigate('Sample')
               }
            />
        <Button
              title="Sample List Screen2!"
              color="#484848"
              onPress={() =>
                 navigation.navigate('Printer2', {data: ''})
               }
            />
        {/*
            register the ref, onMessage, onLoadEnd. we call the refreshPrinters when the webview finished loading
        */}
        <WebView
                ref={ref => this.webviewRef = ref}
                  source={{ html: customHTML }}
                  onMessage={onMessage}
                  onLoadEnd={refreshPrinters}
                  scalesPageToFit={true} androidLayerType={'software'}
                />
    </SafeAreaView>
  );
};

function PrinterScreen2 ({navigation, route}) {
  const { data } = route.params;
  return (
    <PrinterListView
              style={{ flex: 1 }}
              dataText = {data}
              />
  );
};

const SampleListScreen = ({navigation}) => {
  return (
    <SampleList
              style={{ flex: 1 }}
              />
  );
};

const styles = StyleSheet.create({
  sectionContainer: {
    marginTop: 32,
    paddingHorizontal: 24,
  },
  sectionTitle: {
    fontSize: 24,
    fontWeight: '600',
  },
  sectionDescription: {
    marginTop: 8,
    fontSize: 18,
    fontWeight: '400',
  },
  highlight: {
    fontWeight: '700',
  },
});

export default App;
