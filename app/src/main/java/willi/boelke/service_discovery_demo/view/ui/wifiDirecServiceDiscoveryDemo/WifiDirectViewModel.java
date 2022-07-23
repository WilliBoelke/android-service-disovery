package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class WifiDirectViewModel extends ViewModel
{

    private final MutableLiveData<String> mText;

    public WifiDirectViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
    }

    public LiveData<String> getText()
    {
        return mText;
    }
}