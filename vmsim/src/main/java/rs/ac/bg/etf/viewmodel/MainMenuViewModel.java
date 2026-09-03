package rs.ac.bg.etf.viewmodel;

import rs.ac.bg.etf.viewmodel.listeners.MainMenuNavigationListener;

public class MainMenuViewModel 
{
    private final MainMenuNavigationListener navigationListener;

    public MainMenuViewModel(MainMenuNavigationListener navigationListener) 
    {
        this.navigationListener = navigationListener;
    }

    public void executeStartNavigation() 
    {
        System.out.println("MainMenuViewModel: Start button clicked.");
        
        navigationListener.onMainMenuToStart();
        
    }

    public void executeSettingsRequest() 
    {
        System.out.println("MainMenuViewModel: Settings button clicked.");
       
        navigationListener.onMainMenuToSettings();
        
    }
}
