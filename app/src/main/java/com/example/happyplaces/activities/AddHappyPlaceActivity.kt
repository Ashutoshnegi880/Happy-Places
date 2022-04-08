package com.example.happyplaces.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.happyplaces.R
import com.example.happyplaces.database.DatabaseHandler
import com.example.happyplaces.databinding.ActivityAddHappyPlaceBinding
import com.example.happyplaces.models.HappyPlaceModel
import com.example.happyplaces.utils.GetAddressFromLatLng
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {

    private val cal = Calendar.getInstance()
    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener

    private var saveImageToInternalStorage: Uri? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0

    private var mHappyPlaceDetails : HappyPlaceModel? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private lateinit var galleryImageResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var binding: ActivityAddHappyPlaceBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddHappyPlaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarAddPlace)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarAddPlace.setNavigationOnClickListener {
            onBackPressed()
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if(!Places.isInitialized()){
            Places.initialize(this@AddHappyPlaceActivity,
                resources.getString(R.string.google_maps_api_key))
        }


        if (intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)){
            mHappyPlaceDetails = intent.getParcelableExtra(
                MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel?
        }

        dateSetListener = DatePickerDialog.OnDateSetListener {
                view, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }
        updateDateInView()

        if (mHappyPlaceDetails != null){
            supportActionBar?.title = "Edit Happy Place"
            binding.etTitle.setText(mHappyPlaceDetails!!.title)
            binding.etDescription.setText(mHappyPlaceDetails!!.description)
            binding.etDate.setText(mHappyPlaceDetails!!.date)
            binding.etLocation.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude

            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)

            binding.ivPlaceImage.setImageURI(saveImageToInternalStorage)

            binding.btnSave.text = "UPDATE"
        }

        binding.etDate.setOnClickListener(this)
        binding.tvAddImage.setOnClickListener(this)
        binding.btnSave.setOnClickListener(this)
        binding.etLocation.setOnClickListener(this)
        binding.tvSelectCurrentLocation.setOnClickListener(this)



        registerOnActivityForResult()



    }
    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallBack, Looper.myLooper())
    }

    private val mLocationCallBack = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult?.lastLocation
            mLatitude = mLastLocation.latitude
            mLongitude = mLastLocation.longitude
            Log.i("current latitude: ", "$mLatitude")
            Log.i("current longitude: ", "$mLongitude")

            val addressTask = GetAddressFromLatLng(this@AddHappyPlaceActivity, mLatitude, mLongitude)
            addressTask.setAddressListener(object: GetAddressFromLatLng.AddressListener{
                override fun onAddressFound(address: String?){
                    et_location.setText(address)
                }
                override fun onError(){
                    Log.e("Get Address: ", "Something went wrong")
                }
            })
            addressTask.getAddress()
        }
    }



    override fun onClick(v: View?) {
        when(v!!.id){
            R.id.et_date -> {
                DatePickerDialog(this@AddHappyPlaceActivity,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show()
            }

            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")
                val pickerDialogItems = arrayOf("Select photo from Gallery",
                "Capture photo from camera")

                pictureDialog.setItems(pickerDialogItems){
                    _, which->
                    when(which){
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }
                pictureDialog.show()
            }
            R.id.btn_save -> {
                when{
                    binding.etTitle.text.isNullOrEmpty() ->{
                        Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show()
                    }
                    binding.etDescription.text.isNullOrEmpty() ->{
                        Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
                    }
                    binding.etLocation.text.isNullOrEmpty() ->{
                        Toast.makeText(this, "Please enter location", Toast.LENGTH_SHORT).show()
                    }
                    saveImageToInternalStorage == null ->{
                        Toast.makeText(this, "Please  select an image", Toast.LENGTH_SHORT).show()

                    }else -> {
                        val happyPlaceModel = HappyPlaceModel(
                            if(mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,
                            binding.etTitle.text.toString(),
                            saveImageToInternalStorage.toString(),
                            binding.etDescription.text.toString(),
                            binding.etDate.text.toString(),
                            binding.etLocation.text.toString(),
                            mLatitude,
                            mLongitude
                        )
                        val dbHandler = DatabaseHandler(this)
                        if (mHappyPlaceDetails == null) {
                            val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)
                            if (addHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        }else {
                            val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)
                            if (updateHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        }
                    }
                }
            }
            R.id.et_location -> {
                try {
                    //This is list of fields which has to be passed
                    val fields = listOf(
                        Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
                        Place.Field.ADDRESS
                    )
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                        .build(this@AddHappyPlaceActivity)

                    resultPlacesApi.launch(intent)

                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
            R.id.tv_select_current_location -> {
                if (!isLocationEnabled()){
                    Toast.makeText(
                        this,
                        "Your location provider is turned off. please turn that on!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }else{
                    Dexter.withContext(this).withPermissions(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    ).withListener(object: MultiplePermissionsListener {
                        override fun onPermissionsChecked(
                            report: MultiplePermissionsReport?)
                        {
                            if (report!!.areAllPermissionsGranted()){
                                requestNewLocationData()
                            }
                        }
                        override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken)
                        {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread().check()
                }
            }
        }
    }

    var resultPlacesApi = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback{result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data

                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                binding.etLocation.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude

            }else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
                val data: Intent? = result.data
                val status: Status = Autocomplete.getStatusFromIntent(data!!)
                Log.e("error: ", status.statusMessage.toString())
                Toast.makeText(
                    this,
                    status.statusMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    //better method for startActivityFor result
    var resultLauncherCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback{result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data

                val thumbNail : Bitmap = data!!.extras?.get("data") as Bitmap

                saveImageToInternalStorage = saveImageToInternalStorage(thumbNail)
                Log.e("Saved image: ", "$saveImageToInternalStorage")
                binding.ivPlaceImage.setImageBitmap(thumbNail)
            }
        }
    )
    ////////////////// OR /////////

//    var resultLauncherCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            // There are no request codes
//            val data: Intent? = result.data
//
//            val thumbNail : Bitmap = data!!.extras?.get("data") as Bitmap
//            binding.ivPlaceImage.setImageBitmap(thumbNail)
//        }
//    }

    //Using new method to handle startActivityForResult()
    private fun registerOnActivityForResult(){
        //returns: the launcher that can be used to start the activity or dispose of the prepared call.
        galleryImageResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                if(data!=null){
                    val contentUri=data.data
                    try{
//                        val selectedImageBitmap:Bitmap=MediaStore.Images.Media.getBitmap(this.contentResolver,contentUri)
//                        binding.selectedImageImageView.setImageBitmap(selectedImageBitmap)
                        //OR
                        //binding.selectedImageImageView.setImageURI(contentUri)
                        //val selectedImageBitmap:Bitmap=MediaStore.Images.Media.getBitmap(this.contentResolver,contentUri)

                        saveImageToInternalStorage = saveImageToInternalStorage(MediaStore.Images.Media.getBitmap(this.contentResolver,contentUri))
                        Log.e("Saved image: ", "$saveImageToInternalStorage")
                        binding.ivPlaceImage.setImageURI(contentUri)
                    }
                    catch (e: IOException){
                        e.printStackTrace()
                        Toast.makeText(this, "Failed to load image from gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    }

    private fun takePhotoFromCamera(){
        Dexter.withContext(this).withPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
        ).withListener(object: MultiplePermissionsListener {
            override fun onPermissionsChecked(
                report: MultiplePermissionsReport?)
            {
                if (report!!.areAllPermissionsGranted()){

                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                    resultLauncherCamera.launch(cameraIntent)
                }
            }
            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken)
            {
                showRationalDialogForPermissions()
            }
        }).onSameThread().check()
    }

    private fun choosePhotoFromGallery() {
        Dexter.withContext(this).withPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ).withListener(object: MultiplePermissionsListener {
            override fun onPermissionsChecked(
                report: MultiplePermissionsReport?)
            {
                if (report!!.areAllPermissionsGranted()){

                    val galleryIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                    //startActivityForResult(galleryIntent, GALLERY)
                    galleryImageResultLauncher.launch(galleryIntent)

                    }
            }
            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken)
            {
                showRationalDialogForPermissions()
            }
        }).onSameThread().check()
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage(""+
        "It looks like you have turned off permissions required "+
        "for this feature. It can be enabled under the "+
        "Application Settings")
            .setPositiveButton("GO TO SETTINGS")
            {_, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }

            }.setNegativeButton("Cancel"){ dialog, _ ->
                    dialog.dismiss()
            }.show()
    }

    private fun updateDateInView(){
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        binding.etDate.setText(sdf.format(cal.time).toString())
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri{
        val wrapper = ContextWrapper(applicationContext)
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        file = File(file,"${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()

        }catch (e: IOException){
            e.printStackTrace()
        }
        return Uri.parse(file.absolutePath)
    }

    companion object{
//        private const val GALLERY = 1
//        private const val CAMERA = 2
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }
}