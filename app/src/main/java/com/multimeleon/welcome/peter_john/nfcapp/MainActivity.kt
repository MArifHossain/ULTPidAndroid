package com.multimeleon.welcome.peter_john.nfcapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.find
import org.jetbrains.anko.toast
import java.math.BigDecimal
import android.view.MenuItem
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.driverList
import com.opencsv.CSVReader
import java.io.InputStreamReader
import java.io.Serializable
import android.widget.ArrayAdapter
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.maxDimControlVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.maxDimCurrent
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.maxDimToOffVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.maxFullBrightVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.maxOutputCurrent
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.minDimControlVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.minDimCurrent
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.minDimToOffVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.minFullBrightVoltage
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.minOutputCurrent
import com.multimeleon.welcome.peter_john.nfcapp.ULTConfigurationOptions.reconfigureOptions
import kotlinx.android.synthetic.main.search_row.*
import kotlin.experimental.and
import kotlin.experimental.or


var LINEAR_CURVE = 0b00000000
var SOFT_START_CURVE = 0b00010000
var LOGERITHMIC_CURVE = 0b00001000


/**
 * Created by joebakalor on 11/7/17.
 */

class MainActivity : AppCompatActivity() {

    var SEARCH: Int = 1
    var SEARCHRESULT: Int = 2
    private var mNfcAdapter: NfcAdapter? = null
    var read = true
    var driverDictionary:MutableList<List<String>> = mutableListOf()
    var tempDictionary:MutableList<MutableList<String>> = mutableListOf()
    var catalogDictionary:MutableList<List<String>> = mutableListOf()
    var rdocIndex:Int = 0
    var driverRanges:MutableList<List<String>> = mutableListOf()
    var driverListAdapter: ArrayAdapter<String>? = null

    //CONVIENECE
    var currentConfig = NFCUtil.ultConfigManager.pendingConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        readDriversCSV()
        //get the spinner from the xml.
        val dropdown = findViewById<Spinner>(R.id.driverpnSpinner) as Spinner
        //create an adapter to describe how the items are displayed, load the list of items from the string array resource
        driverListAdapter = ArrayAdapter(this, R.layout.spinner_item, ULTConfigurationOptions.driverList)
        //set the spinners adapter to the previously created one.
        dropdown.adapter = driverListAdapter

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        ULTConfigurationOptions.setupOptions()
        setupUI()
        readRanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.app_menu, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        println("onResume")
        mNfcAdapter?.let {
            NFCUtil.enableNFCInForeground(it, this, javaClass)
        }
    }

    override fun onPause() {
        println("onPause")
        super.onPause()
        mNfcAdapter?.let {
            NFCUtil.disableNFCInForeground(it, this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        println("onNewIntent")
        super.onNewIntent(intent)

        if (read == true) {//READ CURRENT CONFIGURATION

            //val messageWrittenSuccessfully = NFCUtil.ULTWriteConfiguration(intent)
            val messageReadSuccessfully = NFCUtil.ULTReadConfiguration(intent, false)
            //LET USER KNOW IF THE DRIVER WAS CONFIGURED SUCCESSFULLY
            toast(messageReadSuccessfully.ifElse("Successful Read from Tag", "Tag Communication Interrupted"))
            updateUI()

        } else {//WRITE USER CONFIGURATION

            //FIRST POPULATE VALUES NOT CONFIGURED BY USER SO WE DONT OVERWRITE WITH BAD DATA
            val configReadSuccessfully = NFCUtil.ULTReadConfiguration(intent, true)
            //NOW WRITE THE CONFIGURATION
            if (configReadSuccessfully){
                val messageWrittenSuccessfully = NFCUtil.ULTWriteConfiguration(intent)//NFCUtil.createNFCMessage("FAKE MESSAGE", intent)
                //LET USER KNOW IF THE DRIVER WAS CONFIGURED SUCCESSFULLY
                toast(messageWrittenSuccessfully.ifElse("Successful Written to Tag", "Tag Communication Interrupted"))
            } else {
                toast("Tag Communication Interrupted")
            }

        }

    }

    fun updateUI() {
        setControlsEnabled(true)

        // Show the Driver name TextView
        this.readDriverpn.visibility = View.VISIBLE
        this.readDriverpn.text = lookupCatalogNumber(NFCUtil.ultConfigManager.driverCatalogIDString)
        setSliderValues(this.readDriverpn.text as String)

        outputCurrentSpinner.setSelection((NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent.toInt() - minOutputCurrent))
        //outputCurrentSlider.setProgress(((NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent.toInt() - minOutputCurrent) * (maxOutputCurrent - minOutputCurrent)) / (maxOutputCurrent - minOutputCurrent))

        minDimCurrentSpinner.setSelection((NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent.toInt()) - minDimCurrent)
        //minDimCurrentSlider.setProgress(((NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent.toInt() - minDimCurrent) * (maxDimCurrent - minDimCurrent)) / (maxDimCurrent - minDimCurrent))

        fullBrightVoltageSpinner.setSelection((NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage.toInt()) - minFullBrightVoltage)
        //fullBrightVoltageSlider.setProgress((NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage.toInt() - minFullBrightVoltage) * (maxFullBrightVoltage - minFullBrightVoltage) / (maxFullBrightVoltage - minFullBrightVoltage))

        minDimVoltageSpinner.setSelection((NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage.toInt()) - minDimControlVoltage)
        //minDimCurrentSlider.setProgress((NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage.toInt() - minDimControlVoltage) * (maxDimControlVoltage - minDimControlVoltage) / (maxDimControlVoltage - minDimControlVoltage))

        dimToOffVoltageSpinner.setSelection(NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage.toInt() -  minDimToOffVoltage)
        //dimToOffVoltageSlider.setProgress((NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage.toInt() -  minDimToOffVoltage) * (maxDimToOffVoltage - minDimToOffVoltage) / (maxDimToOffVoltage - minDimToOffVoltage))
        //TO DO: SET OTHER CONFIGURABLE PARAMTERS

        if(NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve == LINEAR_CURVE.toShort()) {
            dimCurveLinearBtn.isChecked = true
            dimCurveSftStrtBtn.isChecked = false
            dimCurveLogBtn.isChecked = false
        }
        else if(NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve == SOFT_START_CURVE.toShort()) {
            dimCurveLinearBtn.isChecked = false
            dimCurveSftStrtBtn.isChecked = true
            dimCurveLogBtn.isChecked = false
        }
        else if(NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve > LOGERITHMIC_CURVE.toShort()) {
            var pos = (NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve - LOGERITHMIC_CURVE.toShort())
            dimCurveSpinner.setSelection(pos)
            dimCurveLinearBtn.isChecked = false
            dimCurveSftStrtBtn.isChecked = false
            dimCurveLogBtn.isChecked = true
        }

        // Disable controls after updating values
        setControlsEnabled(false)
    }


    //SETUP UI OBJECTS AND EVENT HANDLERS
    fun setupUI() {

        //  TO DO:  NEED TO ADD ANOTHER SPINNER TO ALLOW USER TO SELECT CORRECT DRIVER.
        //
        //  DEPENDING ON THE CONFIGURATION PARAMETERS AVAIALABLE FOR
        //  THE SELECTED DRIVER, DISABLE/ENABLE THE APPRPRIATE UI COMPONENTS,
        //  FOR EXAMPLE TO DISABLE USER ACCESS TO SETTING THE FULL BRIGHT CONTROL VOLTAGE, YOU CAN USE THE FOLLOWING CODE
        //
        //  fullBrightVoltageSpinner.isEnabled = false
        //
        //  This will disable and gray out the control on the user interface

        initControls()

        //SET DEFAULT BUTTON STATE TO WRITE
        writeToggleButton.callOnClick()
        read = false
        this.readDriverpn.visibility = View.GONE

        //SET HANDLER FOR WRITE TOGGLE BUTTON
        this.writeToggleButton.setOnClickListener(View.OnClickListener { view ->
            println("write button is selected, de-select read and reset")
            read = false
            view.background = getDrawable(R.drawable.button_highlight)
            (view as Button).setTextColor(Color.WHITE)
            readToggleButton.background = getDrawable(R.drawable.button_border)
            readToggleButton.setTextColor(Color.BLACK)
            resetButton.background = getDrawable(R.drawable.button_border)
            resetButton.setTextColor(Color.BLACK)

            if(this.readDriverpn.text == "") {
                this.driverpnSpinner.visibility = View.VISIBLE
                this.readDriverpn.visibility = View.GONE

                if(driverpnSpinner.selectedItemPosition != 0) {
                    setControlsEnabled(true)
                }
            } else {
                setControlsEnabled(true)
            }
        })

        //SET HANDLER FOR READ TOGGLE BUTTON
        this.readToggleButton.setOnClickListener(View.OnClickListener { view ->
            println("read button is selected, de-select write and reset")
            read = true
            view.background = getDrawable(R.drawable.button_highlight)
            (view as Button).setTextColor(Color.WHITE)
            writeToggleButton.background = getDrawable(R.drawable.button_border)
            writeToggleButton.setTextColor(Color.BLACK)
            resetButton.background = getDrawable(R.drawable.button_border)
            resetButton.setTextColor(Color.BLACK)

            this.driverpnSpinner.setSelection(0)
            resetAll()
            // Remove the dropdown
            this.driverpnSpinner.visibility = View.GONE

            setControlsEnabled(false)
        })

        //SET HANDLER FOR RESET TOGGLE BUTTON
        this.resetButton.setOnClickListener(View.OnClickListener { view ->
            println("reset button is selected, de-select read and write")
            read = false
            view.background = getDrawable(R.drawable.button_highlight)
            (view as Button).setTextColor(Color.WHITE)
            readToggleButton.background = getDrawable(R.drawable.button_border)
            readToggleButton.setTextColor(Color.BLACK)
            writeToggleButton.background = getDrawable(R.drawable.button_border)
            writeToggleButton.setTextColor(Color.BLACK)

            this.readDriverpn.visibility = View.GONE
            this.readDriverpn.text = ""
            this.driverpnSpinner.visibility = View.VISIBLE

            this.driverpnSpinner.setSelection(0)

            resetAll()

            setControlsEnabled(false)
        })

        //LINEAR DIMMING CURVE RADIO BUTTON: Linear
        dimCurveLinearBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                println("Dim Curve Linear Button Set")
                //RADIO BUTTONS ARE MUTUALLY EXCLUSIVE, SET APPROPRIATELY
                dimCurveSftStrtBtn.isChecked = false
                dimCurveLogBtn.isChecked = false
                dimCurveSpinner.isEnabled = false

                NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve = LINEAR_CURVE.toShort()

            }
        }

        //SOFT START DIMMING CURVE RADIO BUTTON: Soft Start
        dimCurveSftStrtBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                println("Dim Curve Soft Start Button Set")
                //RADIO BUTTONS ARE MUTUALLY EXCLUSIVE, SET APPROPRIATELY
                dimCurveLinearBtn.isChecked = false
                dimCurveLogBtn.isChecked = false
                dimCurveSpinner.isEnabled = false

                NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve = SOFT_START_CURVE.toShort()
            }
        }

        //LOGARITHMIC DIMMING CURVE RADIO BUTTON: Exponential
        dimCurveLogBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                println("Dim Curve Log Button Set")
                //RADIO BUTTONS ARE MUTUALLY EXCLUSIVE, SET APPROPRIATELY
                dimCurveSftStrtBtn.isChecked = false
                dimCurveLinearBtn.isChecked = false
                dimCurveSpinner.isEnabled = true

                NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve = LOGERITHMIC_CURVE.toShort()
            }
        }

        //DEBUG PRINT CONFIG STATUS
        fun printConfig() {
            NFCUtil.ultConfigManager.printPendingConfig()
        }

        //==================  OUTPUT CURRENT: SLIDER AND SPINNER EVENT HANDLERS

        var leavingOutputCurrentSlider = false
        var leavingOutputCurrentSpinner = false

        //OUTPUT CURRENT SLIDER
        this.outputCurrentSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                leavingOutputCurrentSlider = true
                if (leavingOutputCurrentSpinner == false) {
                    outputCurrentSpinner.setSelection(progress)
                } else {
                    leavingOutputCurrentSpinner = false
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                outputCurrentSpinner.setSelection(outputCurrentSlider.progress)
                NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent = (ULTConfigurationOptions.outputPowerOptionSet[outputCurrentSlider.progress]).toShort()
            }
        })

        //OUPUT CURRENT SPINNER
        this.outputCurrentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                leavingOutputCurrentSpinner = true
                outputCurrentSlider.setProgress(position, true)
                leavingOutputCurrentSpinner = false

                //SET VALUE IN TAG MEM MAP
                NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent = (ULTConfigurationOptions.outputPowerOptionSet[position]).toShort()
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //========================  MIN DIM CURRENT: SLIDER AND SPINNER EVENT HANDLERS

        var leavingMinDimCurrentSlider = false
        var leavingMinDimCurrentSpinner = false

        //MIN DIM CURRENT SLIDER
        this.minDimCurrentSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                //GET PERCENT VALUE OF TOTAL
                var percentSpinner = ((((progress).toDouble()) / ((ULTConfigurationOptions.minDimCurrentOptionSet.last() - ULTConfigurationOptions.minDimCurrentOptionSet.first())).toDouble()) * 100).toInt()

                if (percentSpinner > 99) {
                    percentSpinner = 100
                }

                //SET APPROPRIATE SPINNER VALUE BASED ON SLIDER POSITION
                //NEW CONFIGURATION IS WRITTEN TO ULTPendingConfiguration IN SPINNER LISTENER,
                //WHICH IS CALLED AS A RESULT OF SETTING THE SPINNER SELECTION HERE
                leavingMinDimCurrentSlider = true
                minDimCurrentSpinner.setSelection(progress)
                minDimCurrentPctSpinner.setSelection(percentSpinner)
                leavingMinDimCurrentSpinner = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                minDimCurrentSpinner.setSelection(minDimCurrentSlider.progress)
                NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent = (ULTConfigurationOptions.minDimCurrentOptionSet[minDimCurrentSlider.progress]).toShort()

            }
        })

        //MIN DIM CURRENT SPINNER - mA SELECTION
        this.minDimCurrentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                leavingMinDimCurrentSpinner = true
                if (leavingMinDimCurrentSlider == false) {
                    minDimCurrentSlider.setProgress(position, true)
                } else {
                    leavingMinDimCurrentSlider = false
                }

                NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent = (ULTConfigurationOptions.minDimCurrentOptionSet[position]).toShort()
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //MIN DIM CURRENT SPINNER - PERCENT SELECTION
        this.minDimCurrentPctSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                leavingMinDimCurrentSpinner = true
                if (leavingMinDimCurrentSlider == false) {
                    minDimCurrentSlider.setProgress(position, true)
                } else {
                    leavingMinDimCurrentSlider = false
                }
                //SET VALUE IN TAG MEM MAP
                NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent = (position).toShort()//finalSetting.toShort()

                //PRINT CONFIG FOR DEBUGGING
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //====================  FULL BRIGHT VOLTAGE: SLIDER AND SPINNER EVENT HANDLERS

        var leavingFullBrightVoltageSlider = false
        var leavingFullBrightVoltageSpinner = false

        //FULL BRIGHT VOLTAGE SLIDER
        this.fullBrightVoltageSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

                leavingFullBrightVoltageSlider = true
                if (leavingFullBrightVoltageSpinner == false) {
                    fullBrightVoltageSpinner.setSelection(progress)
                } else {
                    leavingFullBrightVoltageSpinner = false
                }
                //SET APPROPRIATE SPINNER VALUE BASED ON SLIDER POSITION

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                fullBrightVoltageSpinner.setSelection(fullBrightVoltageSlider.progress)
                //SET VALUE IN TAG MEM MAP
                NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage = (((ULTConfigurationOptions.fullBrightVoltageOptionSet[fullBrightVoltageSlider.progress]).toDouble() / 10) * 1000).toShort()
            }
        })

        //FULL BRIGHT VOLTAGE SPINNER
        this.fullBrightVoltageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                leavingFullBrightVoltageSpinner = true
                fullBrightVoltageSlider.setProgress(position, true)
                leavingFullBrightVoltageSlider = false

                //SET VALUE IN TAG MEM MAP
                NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage = (((ULTConfigurationOptions.fullBrightVoltageOptionSet[position]).toDouble() / 10) * 1000).toShort()

                //PRINT CONFIG FOR DEBUGGING
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //============MIN DIM VOLTAGE
        //MIN DIM VOLTAGE SLIDER
        var leavingMinDimVoltageSlider = false
        var leavingMinDimVoltageSpinner = false

        this.minDimVoltageSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                //SET APPROPRIATE SPINNER VALUE BASED ON SLIDER POSITION
                leavingMinDimVoltageSlider = true
                if (leavingMinDimVoltageSpinner == false) {
                    minDimVoltageSpinner.setSelection(progress)
                } else {
                    leavingMinDimVoltageSpinner = false
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                minDimVoltageSpinner.setSelection(minDimVoltageSlider.progress)
                NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage = ((ULTConfigurationOptions.minDimVoltageOptionSet[minDimVoltageSlider.progress].toDouble() / 10) * 1000).toShort()

            }
        })

        //MIN DIM VOLTAGE SPINNER
        this.minDimVoltageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                //SET VALUE IN TAG MEM MAP
                leavingMinDimVoltageSpinner = true
                minDimVoltageSlider.setProgress(position, true)
                leavingMinDimVoltageSlider = false

                NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage = ((ULTConfigurationOptions.minDimVoltageOptionSet[position].toDouble() / 10) * 1000).toShort()
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //====================DIM TO OFF

        //DIM TO OFF VOLTAGE SLIDER

        var leavingDimToOffVoltageSlider = false
        var leavingDimToOffVoltageSpinner = false

        this.dimToOffVoltageSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                leavingDimToOffVoltageSlider = true
                if (leavingDimToOffVoltageSpinner == false) {
                    //SET APPROPRIATE SPINNER VALUE BASED ON SLIDER POSITION
                    dimToOffVoltageSpinner.setSelection(progress)
                } else {
                    leavingDimToOffVoltageSpinner = false
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                dimToOffVoltageSpinner.setSelection(dimToOffVoltageSlider.progress)
                NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage = ((ULTConfigurationOptions.dimToOffVoltageOptionset[dimToOffVoltageSlider.progress].toDouble() / 10) * 1000).toShort()

            }
        })

        //DIM TO OFF VOLTAGE SPINNER
        this.dimToOffVoltageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                leavingDimToOffVoltageSpinner = true
                dimToOffVoltageSlider.setProgress(position, true)
                leavingDimToOffVoltageSlider = false

                //SET VALUE IN TAG MEM MAP
                NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage = ((ULTConfigurationOptions.dimToOffVoltageOptionset[position].toDouble() / 10) * 1000).toShort()
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //DRIVER P/N SPINNER
        this.driverpnSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                if(position != 0) {
                    var filteredDrivers:MutableList<List<String>> = driverDictionary
                    filteredDrivers = filteredDrivers.filter(fun(row) = row[0] == (ULTConfigurationOptions.driverList[position])).toMutableList()

                    //setUIValuesforDriver(filteredDrivers[0])
                } else {
                    resetAll()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }


        //LOGERITHMIC INDEX SPINNER
        this.dimCurveSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                //leavingDimToOffVoltageSpinner = true
                //dimToOffVoltageSlider.setProgress(position, true)
                //leavingDimToOffVoltageSlider = false

                //SET VALUE IN TAG MEM MAP
                var dimVal = ((LOGERITHMIC_CURVE.toByte()).or((position.toByte()))).toShort()
                NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve = dimVal
                printConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun resetAll() {
        initControls()

        outputCurrentSpinner.setSelection(0)
        minDimCurrentSpinner.setSelection(0)
        minDimCurrentSlider.progress = 0
        dimCurveLinearBtn.isChecked = false
        dimCurveSftStrtBtn.isChecked = false
        dimCurveLogBtn.isChecked = false
        dimCurveSpinner.setSelection(0)
        dimCurveSpinner.isEnabled = false

        fullBrightVoltageSpinner.setSelection(0)
        fullBrightVoltageSlider.progress = 0

        minDimVoltageSpinner.setSelection(0)
        minDimVoltageSlider.progress = 0

        dimToOffVoltageSpinner.setSelection(0)
        dimToOffVoltageSlider.progress = 0

        NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent = MIN_OUTPUT_CURRENT.toShort()
        NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent = MIN_DIM_CURRENT.toShort()
        NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage = MIN_FULL_BRIGHT_VOLTAGE.toShort()
        NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage = MIN_DIM_CONTROL_VOLTAGE.toShort()
        NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage = MIN_DIM_TO_OFF_CONTROL_VOLTAGE.toShort()
        NFCUtil.ultConfigManager.pendingConfiguration.dimmingCurve = DEFAULT_DIM_CURVE.toShort()
    }

    private fun initControls() {
        //SETUP SPINNER VALUES
        val outputCurrentSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.outputPowerList)
        this.outputCurrentSpinner.adapter = outputCurrentSpinnerAdapter

        val minDimCurrentSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.minDimCurrentList)
        this.minDimCurrentSpinner.adapter = minDimCurrentSpinnerAdapter

        val fullBrightVoltageSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.fullBrightControlVoltageList)
        this.fullBrightVoltageSpinner.adapter = fullBrightVoltageSpinnerAdapter

        val minDimVoltageSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.minDimControlVoltageList)
        this.minDimVoltageSpinner.adapter = minDimVoltageSpinnerAdapter

        val dimToOffVoltageSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.dimToOffControlVoltageList)
        this.dimToOffVoltageSpinner.adapter = dimToOffVoltageSpinnerAdapter

        val minDimCurrentPctSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.minDimCurrentPctList)
        this.minDimCurrentPctSpinner.adapter = minDimCurrentPctSpinnerAdapter

        val dimCurveLogSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.dimCurveLogarithmicList)
        this.dimCurveSpinner.adapter = dimCurveLogSpinnerAdapter


        setSliderValues("")

        setControlsEnabled(false)
    }

    private fun setSliderValues(driverName: String) {
        reconfigureOptions(driverName)

        //SET SLIDER MAX VALUES. MAX VALUE IS EQUAL TO THE MAX NUMBER IN THE RANGE
        outputCurrentSlider.max = maxOutputCurrent - minOutputCurrent// From ULTConfigurationOptions
        minDimCurrentSlider.max = maxDimCurrent - minDimCurrent
        fullBrightVoltageSlider.max = maxFullBrightVoltage - minFullBrightVoltage
        minDimVoltageSlider.max = maxDimControlVoltage - minDimControlVoltage
        dimToOffVoltageSlider.max = maxDimToOffVoltage - minDimToOffVoltage
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {

                val intent1 = Intent(this, SearchActivity::class.java)
                this.startActivityForResult(intent1, SEARCH)
                return true
            }

            else -> {
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item)
            }
        }
    }

    fun searchClick(view: View) {
        val intent1 = Intent(this, SearchActivity::class.java)
        this.startActivityForResult(intent1, SEARCH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        try {
            super.onActivityResult(requestCode, resultCode, intent)
            if(resultCode == Activity.RESULT_OK) {

                when(requestCode)
                {
                    SEARCH -> {
                        val extras = intent!!.extras
                        if (extras != null) {
                            var driverList = extras.get("DriverList") as MutableList<List<String>>

                            val searchResultIntent = Intent(this, SearchResult::class.java)
                            searchResultIntent.putExtra("DriverList", driverList as Serializable)
                            this.startActivityForResult(searchResultIntent, SEARCHRESULT)
                        }
                    }
                    SEARCHRESULT -> {
                        val extras = intent!!.extras
                        if (extras != null) {
                            var selectedDriver = extras.get("DriverList") as List<String>
                            this.driverpnSpinner.setSelection(driverListAdapter!!.getPosition(selectedDriver[0]))
                            this.readDriverpn.text = ""
                            setUIValuesforDriver(selectedDriver)
                            writeToggleButton.callOnClick()
                        }
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUIValuesforDriver(selectedDriver: List<String>) {
        setControlsEnabled(true)

        setSliderValues(selectedDriver[0])

        var outputCurrent = selectedDriver[2].toInt()

        val outputCurrentSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ULTConfigurationOptions.outputPowerList)
        this.outputCurrentSpinner.adapter = outputCurrentSpinnerAdapter

        outputCurrentSpinner.setSelection(outputCurrent - minOutputCurrent)
        outputCurrentSlider.progress = ((outputCurrent - minOutputCurrent) * (maxOutputCurrent - minOutputCurrent)) / (maxOutputCurrent - minOutputCurrent)

        NFCUtil.ultConfigManager.pendingConfiguration.outputCurrent = outputCurrent.toShort()

        minDimCurrentSpinner.setSelection(0)
        minDimCurrentSlider.progress = 0
        NFCUtil.ultConfigManager.pendingConfiguration.minDimCurrent = (ULTConfigurationOptions.minDimCurrentOptionSet[0]).toShort()

        dimCurveLinearBtn.isChecked = false
        dimCurveSftStrtBtn.isChecked = false
        dimCurveLogBtn.isChecked = false
        dimCurveSpinner.setSelection(0)
        dimCurveSpinner.isEnabled = false

        fullBrightVoltageSpinner.setSelection(10)
        fullBrightVoltageSlider.progress = 10
        NFCUtil.ultConfigManager.pendingConfiguration.fullBrightControlVoltage = (((ULTConfigurationOptions.fullBrightVoltageOptionSet[10]).toDouble() / 10) * 1000).toShort()

        minDimVoltageSpinner.setSelection(10)
        minDimVoltageSlider.progress = 10
        NFCUtil.ultConfigManager.pendingConfiguration.minDimControlVoltage = ((ULTConfigurationOptions.minDimVoltageOptionSet[10].toDouble() / 10) * 1000).toShort()

        dimToOffVoltageSpinner.setSelection(0)
        dimToOffVoltageSlider.progress = 0
        NFCUtil.ultConfigManager.pendingConfiguration.dimToOffControlVoltage = ((ULTConfigurationOptions.dimToOffVoltageOptionset[0].toDouble() / 10) * 1000).toShort()
    }

    private fun setControlsEnabled(value: Boolean) {
        outputCurrentSpinner.isEnabled = value
        outputCurrentSlider.isEnabled = value
        minDimCurrentSpinner.isEnabled = value
        minDimCurrentSlider.isEnabled = value
        fullBrightVoltageSpinner.isEnabled = value
        fullBrightVoltageSlider.isEnabled = value
        minDimVoltageSpinner.isEnabled = value
        minDimVoltageSlider.isEnabled = value
        dimToOffVoltageSpinner.isEnabled = value
        dimToOffVoltageSlider.isEnabled = value
        minDimCurrentPctSpinner.isEnabled = value
        dimCurveSpinner.isEnabled = value
        dimCurveLinearBtn.isEnabled = value
        dimCurveSftStrtBtn.isEnabled = value
        dimCurveLogBtn.isEnabled = value
    }

    fun advancedClick(view: View) {
        // Remove the Advanced button
        findViewById<TextView>(R.id.advancedButton).visibility = View.GONE

        // Show Dimming Curve
        findViewById<TextView>(R.id.dimmingCurveLabel).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.dimmingCurveLayout).visibility = View.VISIBLE

        // Show Full Bright Control Voltage
        findViewById<TextView>(R.id.fbcvLabel).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.fbcvLayout).visibility = View.VISIBLE

        // Show Min Dim Control Voltage
        findViewById<TextView>(R.id.mdcvLabel).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.mdcvLayout).visibility = View.VISIBLE

        // Show Dim-to-Off Control Voltage
        findViewById<TextView>(R.id.dtocvLabel).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.dtocvLayout).visibility = View.VISIBLE
    }

    private fun readDriversCSV() {

        try {
            // If the file has already been read, nothing to do
            if(driverDictionary.count() > 0)
                return

            val reader = CSVReader(InputStreamReader(assets.open("drivers.csv")))

            var nextLine = reader.readNext()
            while (nextLine != null) {
                // nextLine[] is an array of values from the line
                if(nextLine.joinToString("").isNotBlank())  {
                    tempDictionary.add(nextLine.toMutableList())
                }
                nextLine = reader.readNext()
            }

            for(i in tempDictionary[0].indices) {
                if(tempDictionary[0][i].trim().toLowerCase() == "output_current")
                    rdocIndex = i
            }

            tempDictionary.removeAt(0)

            var driverMap = tempDictionary.groupBy { it[0] }

            driverList.clear()
            driverList.add("[ SELECT DRIVER... ]")
            for(driver in driverMap.keys) {
                var rows = driverMap.get(driver)

                var maxCurrent = rows!!.first()[rdocIndex]
                var minCurrent = rows.last()[rdocIndex]

                driverRanges.add(listOf(driver, maxCurrent, minCurrent))
                driverList.add(driver)
            }

            for(driver in tempDictionary) {
                var catalogName = driver[0]

                var range = driverRanges.filter(fun(row) = row[0] == catalogName)

                driver += range[0][1]
                driver += range[0][2]

                driverDictionary.add(driver.toList())
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    public fun lookupCatalogNumber(catalogID: String) : String {
        try{
            // If the file has already been read, nothing to do
            if(catalogDictionary.count() > 0)
                return catalogDictionary.find(fun(row) = row[0] == catalogID)!![1]

            val reader = CSVReader(InputStreamReader(assets.open("catalog.csv")))

            var nextLine = reader.readNext()
            while (nextLine != null) {
                //if(nextLine. == "")
                    //continue;
                // nextLine[] is an array of values from the line
                if(nextLine.joinToString("").isNotBlank())  {
                    catalogDictionary.add(nextLine.toMutableList())
                }
                nextLine = reader.readNext()
                println("Lookup Catalog Number = $nextLine.")
            }

            return catalogDictionary.find(fun(row) = row[0] == catalogID)!![1]

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        return ""
    }

    private fun readRanges() {
        try{
            // If the file has already been read, nothing to do
            if(ULTConfigurationOptions.rangeDictionary.count() > 0)
                return

            val reader = CSVReader(InputStreamReader(assets.open("ranges.csv")))

            var nextLine = reader.readNext()
            while (nextLine != null) {
                // nextLine[] is an array of values from the line
                if(nextLine.joinToString("").isNotBlank())  {
                    ULTConfigurationOptions.rangeDictionary.add(nextLine.toMutableList())
                }
                nextLine = reader.readNext()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}

object ULTConfigurationOptions{

    //STRING LIST USED FOR DISPLAY
    //NUMERIC LIST USED FOR ACTUAL VALUE STORED
    val outputPowerList = ArrayList<String>()
    var outputPowerOptionSet = ArrayList<Int>()

    val minDimCurrentList = ArrayList<String>()
    var minDimCurrentOptionSet = ArrayList<Int>()

    val fullBrightControlVoltageList = ArrayList<String>()
    var fullBrightVoltageOptionSet = ArrayList<Int>()

    val minDimControlVoltageList = ArrayList<String>()
    var minDimVoltageOptionSet = ArrayList<Int>()

    val dimToOffControlVoltageList = ArrayList<String>()
    var dimToOffVoltageOptionset = ArrayList<Int>()

    val minDimCurrentPctList = ArrayList<String>()

    val dimCurveLogarithmicList = ArrayList<String>()
    var dimCurveLogarithmicOptionSet = ArrayList<Int>()

    var standardMinCurrent = 315
    var standardMaxCurrent = 1050

    var driverList:MutableList<String> = mutableListOf()
    var rangeDictionary:MutableList<List<String>> = mutableListOf()

    var minOutputCurrent : Int = MIN_OUTPUT_CURRENT
    var maxOutputCurrent : Int = MAX_OUTPUT_CURRENT
    var minDimCurrent : Int = MIN_DIM_CURRENT
    var maxDimCurrent : Int = MAX_DIM_CURRENT
    var minDimControlVoltage : Int = MIN_DIM_CONTROL_VOLTAGE
    var maxDimControlVoltage : Int = MAX_DIM_CONTROL_VOLTAGE
    var minFullBrightVoltage : Int = MIN_FULL_BRIGHT_VOLTAGE
    var maxFullBrightVoltage : Int = MAX_FULL_BRIGHT_VOLTAGE
    var minDimToOffVoltage : Int = MIN_DIM_TO_OFF_CONTROL_VOLTAGE
    var maxDimToOffVoltage : Int = MAX_DIM_TO_OFF_CONTROL_VOLTAGE


    //SET OPTION RANGES
    fun setupOptions(){

        //315mA <= OUTPUT CURRENT <=1050mA
        var i: Int = MIN_OUTPUT_CURRENT
        while (i <= MAX_OUTPUT_CURRENT){
            outputPowerList.add("$i mA")
            outputPowerOptionSet.add(i)
            i += 1
        }

        //10mA <= MIN DIM CURRENT <= 263mA
        i = MIN_DIM_CURRENT
        while (i <= MAX_DIM_CURRENT){
            minDimCurrentList.add("$i mA")
            minDimCurrentOptionSet.add(i)
            i += 1
        }

        //LOG CURVE OPTONS
        i = 0
        while (i <= 10){
            dimCurveLogarithmicList.add("$i")
            dimCurveLogarithmicOptionSet.add(i)
            i += 1
        }

        //7V <= FULL BRIGHT VOLTAGE <= 9V
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = MIN_FULL_BRIGHT_VOLTAGE
        while (i <= MAX_FULL_BRIGHT_VOLTAGE){
            fullBrightControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            fullBrightVoltageOptionSet.add(i)
            i += 1
        }

        //0V <= MIN DIM CONTROL VOLTAGE <= 3V
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = MIN_DIM_CONTROL_VOLTAGE
        while (i <= MAX_DIM_CONTROL_VOLTAGE){
            minDimControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            minDimVoltageOptionSet.add(i)
            i += 1
        }

        //0V <= DIM TO OFF CONTROL VOLTAGE <= 1.7V
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = MIN_DIM_TO_OFF_CONTROL_VOLTAGE
        while (i <= MAX_DIM_TO_OFF_CONTROL_VOLTAGE){
            dimToOffControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            dimToOffVoltageOptionset.add(i)
            i += 1
        }

        //0->100%
        i = 0
        while (i <= 100){
            minDimCurrentPctList.add("$i %")
            i += 1
        }
    }

    //RE-CONFIGURE OPTION RANGES
    fun reconfigureOptions(driverName: String){

        var rangeRow = rangeDictionary.find(fun(row) = row[0] == driverName)

        if(driverName.isNullOrBlank() || rangeRow == null) {  // Initialize with standard values if we do not have a specific driver
            minOutputCurrent = MIN_OUTPUT_CURRENT
            maxOutputCurrent = MAX_OUTPUT_CURRENT
            minDimCurrent = MIN_DIM_CURRENT
            maxDimCurrent = MAX_DIM_CURRENT
            minDimControlVoltage = MIN_DIM_CONTROL_VOLTAGE
            maxDimControlVoltage = MAX_DIM_CONTROL_VOLTAGE
            minFullBrightVoltage = MIN_FULL_BRIGHT_VOLTAGE
            maxFullBrightVoltage = MAX_FULL_BRIGHT_VOLTAGE
            minDimToOffVoltage = MIN_DIM_TO_OFF_CONTROL_VOLTAGE
            maxDimToOffVoltage = MAX_DIM_TO_OFF_CONTROL_VOLTAGE

        } else {
            minOutputCurrent = rangeRow[1].toInt()
            maxOutputCurrent = rangeRow[2].toInt()
            minDimCurrent = rangeRow[3].toInt()
            maxDimCurrent = rangeRow[4].toInt()
            minDimControlVoltage = rangeRow[5].toInt()
            maxDimControlVoltage = rangeRow[6].toInt()
            minFullBrightVoltage = rangeRow[7].toInt()
            maxFullBrightVoltage = rangeRow[8].toInt()
            minDimToOffVoltage = rangeRow[9].toInt()
            maxDimToOffVoltage = rangeRow[10].toInt()
        }

        outputPowerList.clear()
        outputPowerOptionSet.clear()
        //minOutputCurrent <= OUTPUT CURRENT <=maxOutputCurrent
        var i: Int = minOutputCurrent
        while (i <= maxOutputCurrent){
            outputPowerList.add("$i mA")
            outputPowerOptionSet.add(i)
            i += 1
        }

        minDimCurrentList.clear()
        minDimCurrentOptionSet.clear()
        //minDimCurrent <= MIN DIM CURRENT <= maxDimCurrent
        i = minDimCurrent
        while (i <= maxDimCurrent){
            minDimCurrentList.add("$i mA")
            minDimCurrentOptionSet.add(i)
            i += 1
        }

        minDimControlVoltageList.clear()
        minDimVoltageOptionSet.clear()
        //minDimControlVoltage <= MIN DIM CONTROL VOLTAGE <= maxDimControlVoltage
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = minDimControlVoltage
        while (i <= maxDimControlVoltage){
            minDimControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            minDimVoltageOptionSet.add(i)
            i += 1
        }

        fullBrightControlVoltageList.clear()
        fullBrightVoltageOptionSet.clear()
        //minFullBrightVoltage <= FULL BRIGHT VOLTAGE <= maxFullBrightVoltage
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = minFullBrightVoltage
        while (i <= maxFullBrightVoltage){
            fullBrightControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            fullBrightVoltageOptionSet.add(i)
            i += 1
        }

        dimToOffControlVoltageList.clear()
        dimToOffVoltageOptionset.clear()
        //minDimToOffVoltage <= DIM TO OFF CONTROL VOLTAGE <= maxDimToOffVoltage
        //USING INTEGER VALUES BECAUSE ANDROID SLIDER ONLY ALLOWS INT, DIVIDE BY 10 BEFORE SET TAG MEM
        i = minDimToOffVoltage
        while (i <= maxDimToOffVoltage){
            dimToOffControlVoltageList.add("${Math.round((i.toDouble()/10) * 100.00)/100.00} V")
            dimToOffVoltageOptionset.add(i)
            i += 1
        }
    }
}

fun <T> Boolean.ifElse(primaryResult: T, secondaryResult: T) = if (this) primaryResult else secondaryResult