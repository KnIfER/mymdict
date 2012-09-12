/*
 * Copyright (C) 2012. Rayman Zhang <raymanzhang@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cn.mdict;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.text.format.Time;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
import cn.mdict.fragments.DictView;
import cn.mdict.mdx.*;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainForm extends SherlockFragmentActivity {
    static {
        System.loadLibrary("iconv");
        System.loadLibrary("mdx");
    }
	private MdxDictBase dict;
//	private Intent searchIntent, libraryIntent, favIntent, historyIntent, infoIntent;
	public static final int kHistoryIntentId=0;
    public static final int kFavoritesIntentId=1;
    public static final int kLibraryIntentId=2;
    public static final int kSettingIntentId=3;


    private boolean startBySearch=false;
    private ActionBar actionBar=null;
    private boolean skipOnResume=false;
    private String lastClipboardText="";
    private Time lastBackPressedTime=null;


	private Handler handler;
    private DictView dictView;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try{
            MdxEngine.initSettings(getApplicationContext());
            if ( Build.VERSION.SDK_INT<Build.VERSION_CODES.ICE_CREAM_SANDWICH ){
                //this.setTheme(R.style.Theme_MDict_ForceOverFlow);
            }
            
            setContentView(R.layout.main_frame);
            actionBar=getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);

            final ImageView imageView = (ImageView)findViewById(R.id.splash_view);
            if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
                imageView.setBackgroundDrawable(getResources().getDrawable(R.drawable.splash_land));
            else
                imageView.setBackgroundDrawable(getResources().getDrawable(R.drawable.splash));

            if ( MdxEngine.getSettings().getPrefShowSplash()){
                actionBar.hide();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            }else {
                imageView.setVisibility(View.GONE);
                ViewGroup rootView = (ViewGroup) MainForm.this.getWindow().getDecorView();
                rootView.removeView(imageView);
            }

            dictView=(DictView) getSupportFragmentManager().findFragmentById(R.id.dict_view_fragment);

            dict=new MdxDictBase();


            AddonFuncUnt.initApp(getApplicationContext(), getAssets(), dict);
            dictView.setDict(dict);
            //dictView.displayWelcome();
            //setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

            handleIntent(getIntent());

            if ( MdxEngine.getSettings().getPrefUseTTS() )
                dictView.initTTSEngine();

            if ( Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB){
                android.content.ClipboardManager clipboardManager= (android.content.ClipboardManager)getSystemService("clipboard");
                clipboardManager.addPrimaryClipChangedListener(new android.content.ClipboardManager.OnPrimaryClipChangedListener(){
                    @Override
                    public void onPrimaryClipChanged() {
                        if ( MdxEngine.getSettings().getPrefMonitorClipboard()){
                            Intent intent = new Intent(getApplicationContext(), MainForm.class);
                            intent.setAction(Intent.ACTION_MAIN);
                            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            //intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                        }
                    }
                });
            }

            if ( MdxEngine.getSettings().getPrefShowSplash()){
                handler=new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        imageView.setVisibility(View.GONE);
                        ViewGroup rootView = (ViewGroup) MainForm.this.getWindow().getDecorView();
                        rootView.removeView(imageView);

                        actionBar.show();
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        if ( !startBySearch )
                            dictView.switchToListView();
                    }
                }, 1000);
            }
            
        }
        catch (Exception e){
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File("/mnt/sdcard/mdict/doc/mdict_j.log"));
            } catch (FileNotFoundException e1) {
                AddonFuncUnt.showMessageDialog(this, "Fail to log stack trace to file", "Error");
            }
            if (fos!=null){
                PrintStream ps = new PrintStream(fos);
                e.printStackTrace(ps);
            }
        }
    }


    static Pattern SearchSuggestData =Pattern.compile("content://mdx[.]mdict[.]cn/(\\d+)_(-?\\d+)_(.*)");

    private void handleIntent(Intent intent) {
        if (dict!=null && dict.isValid()) {
            if ( Intent.ACTION_SEARCH.equals(intent.getAction())) {
                String query = getIntent().getStringExtra(SearchManager.QUERY);
                dictView.displayByHeadword(query, true);
                startBySearch=true;
            }else if ( Intent.ACTION_VIEW.equals(intent.getAction())){
                Uri uri=intent.getData();
                Matcher matcher = SearchSuggestData.matcher(uri.toString());
                if ( matcher.matches() && matcher.groupCount()>=2 ){
                    int dictId=Integer.parseInt(matcher.group(1));
                    int entryNo=Integer.parseInt(matcher.group(2));
                    DictEntry entry=new DictEntry(entryNo, "", dictId);
                    String headWord="";
                    if ( matcher.groupCount()==3 ){
                        headWord=AddonFuncUnt.DecodeUrl(matcher.group(3));
                        entry.setHeadword(headWord);
                    }
                    if ( entry.isUnionDictEntry() ){
                        dictView.displayByHeadword(headWord, true);
                    }else {
                        dictView.displayByEntry(entry, true);
                    }
                    startBySearch=true;
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onPause(){
    	MdxEngine.saveEngineSettings();
    	super.onPause();
    }

    /*
    @Override
    public void onConfigurationChanged (Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if (!MdxEngine.getSettings().getPrefLockRotation())
            setRequestedOrientation(currentOrientation);
    }
    */

    public void quitProcess() {
        //MdxEngine.unregisterNotification();
        MdxEngine.saveEngineSettings();
        MainForm.this.finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    public void onQuit(){
        AlertDialog dialog=AddonFuncUnt.buildConfirmDialog(this,
                R.string.confirm_quit, R.string.quit,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public  void onClick(android.content.DialogInterface dialogInterface, int i) {
                        quitProcess();
                    }}, null);
        dialog.show();
    }


    @Override
    public boolean onSearchRequested(){
        dictView.switchToListView();
        return true;
    }
    @Override
    public boolean onKeyLongPress(int keyCode, android.view.KeyEvent event){
        if (keyCode== KeyEvent.KEYCODE_BACK){
            onQuit();
            return true;
        }else
            return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown (int keyCode, KeyEvent event){
        if ( keyCode>= KeyEvent.KEYCODE_A && keyCode<=KeyEvent.KEYCODE_Z){
			if ( !dictView.isInputing() ){
	    		dictView.switchToListView();
	    	}
    	}
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed(){
        if ( !dictView.onBackPressed() ) {
            Time now = new Time();
            now.setToNow();
            //Check if user pressed back_key twice in 5 seconds.
            if ( lastBackPressedTime==null || (now.toMillis(true)-lastBackPressedTime.toMillis(true))>5*1000 ){
                Toast.makeText(this, R.string.quit_prompt, Toast.LENGTH_LONG).show();
                lastBackPressedTime=now;
            }else {
                quitProcess();
            }
        }
    }

    protected void startIntentForClass(int requestCode, java.lang.Class<?> cls){
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setClass(this, cls);
        startActivityForResult(intent, requestCode);
        skipOnResume=true;
    }
    
    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data){

        switch (requestCode){
            case kLibraryIntentId:
                if (resultCode== Activity.RESULT_OK){
                    MdxEngine.saveEngineSettings();
                    int libId=data.getIntExtra(LibraryFrame.SELECTED_LIB_ID, DictPref.kInvalidDictPrefId);
                    if ( libId!=DictPref.kInvalidDictPrefId ){
                        dictView.selectDict(libId);
                    }
                }
                break;
            case kFavoritesIntentId:
                if (resultCode== Activity.RESULT_OK){
                    DictEntry favEntry=new DictEntry(
                            data.getIntExtra(FavoritesFrame.entryNoName,-1),
                            data.getStringExtra(FavoritesFrame.headwordName),
                            data.getIntExtra(FavoritesFrame.dictIdName, DictPref.kInvalidDictPrefId) );
                    dictView.displayByEntry(favEntry, false);
                }
                break;
            case kHistoryIntentId:
                if (resultCode== Activity.RESULT_OK){
                    DictEntry histEntry=new DictEntry(
                        data.getIntExtra(HistoryFrame.entryNoName,-1),
                        data.getStringExtra(HistoryFrame.headwordName),
                        data.getIntExtra(HistoryFrame.dictIdName, DictPref.kInvalidDictPrefId) );
                    histEntry.makeJEntry();
                    dictView.displayByEntry(histEntry, false);
                }
                break;
            case kSettingIntentId:
                if (data!=null){
                    ArrayList<String> changePrefs=data.getStringArrayListExtra(SettingFrame.prefChanged);
                    if ( changePrefs!=null && changePrefs.size()>0 ){
                        for( String pref:changePrefs){
                            if (pref.compareToIgnoreCase(MdxEngineSetting.prefUseTTS)==0
                                    ||pref.compareToIgnoreCase(MdxEngineSetting.prefPreferredTTSEngine)==0
                                    ||pref.compareToIgnoreCase(MdxEngineSetting.prefTTSLocale)==0 ){
                                dictView.initTTSEngine();
                            }else if (pref.compareToIgnoreCase(MdxEngineSetting.prefUseFingerGesture)==0 ){
                                dictView.enableFingerGesture(MdxEngine.getSettings().getPrefUseFingerGesture());
                            }
                        }
                    }
                }
                if (dict!=null)
                    MdxEngine.rebuildHtmlSetting(dict, MdxEngine.getSettings().getPrefHighSpeedMode());
                if (MdxEngine.getSettings().getPrefShowInNotification())
                    MdxEngine.registerNotification();
                else
                    MdxEngine.unregisterNotification();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        InputMethodManager imm = (InputMethodManager) getSystemService( android.content.Context.INPUT_METHOD_SERVICE );
        if ( imm.isActive() ){
            AddonFuncUnt.hideSIP(this);
        }
        // Handle item selection
/*
        QuickActionBar qbar=new QuickActionBar(this);
        qbar.addQuickAction(new QuickAction(this, R.drawable.ic_search, R.string.quit));
        View itemView=AddonFuncUnt.getItemViewForActionItem((ActionBarImpl)getSupportActionBar(), item);
        qbar.show(itemView);
*/
        switch (item.getItemId()) {
            //case android.R.id.home:
            //    return true;
            case R.id.library:
                startIntentForClass(kLibraryIntentId, LibraryFrame.class);
                return true;
            case R.id.favorites:
                startIntentForClass(kFavoritesIntentId, FavoritesFrame.class);
                return true;
            case R.id.history:
                startIntentForClass(kHistoryIntentId, HistoryFrame.class);
                return true;
            case R.id.settings:
                startIntentForClass(kSettingIntentId, SettingFrame.class);
                return true;
            case R.id.quit:
                onQuit();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (MdxEngine.getSettings().getPrefLockRotation())
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        if ( !skipOnResume ){
            if ( MdxEngine.getSettings().getPrefAutoLookupClipboard() ){
                try{
                    String clipboardText=((ClipboardManager)getSystemService("clipboard")).getText().toString();
                    if ( clipboardText!=null ){
                        if (lastClipboardText==null || !clipboardText.contentEquals(lastClipboardText) ){
                            lastClipboardText=clipboardText;
                            dictView.displayByHeadword(lastClipboardText, true);
                        }
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        skipOnResume=false;
    }

    @Override
    protected void onDestroy(){
        MdxEngine.saveEngineSettings();
        super.onDestroy();
    }

    static {
        System.loadLibrary("iconv");
        System.loadLibrary("mdx");
    }
}