package com.aldrin.grabbingredpackets.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class GrabbingRedPacketsService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "GrabbingRedPacketsServi";

    private final static String WX_RED_PACKETS_GET = "领取红包";
    private final static String WX_GIVE_YOU_RED_PACKETS = "给你发了一个红包";
    private final static String WX_GET = "红包已领取";
    private final static String QQ_CLICK_TO_ENTER_PASSWORD = "点击输入口令";
    private final static String QQ_DEFAULT = "QQ红包";
    private final static String QQ_DEFAULT_PASSWORD = "口令红包";
    private final static String QQ_SEND_BUTTON_TEXT = "发送";
    private boolean mLuckyMoneyReceived;
    private String lastFetchedHongbaoId = null;
    private long lastFetchedTime = 0;
    private static final int MAX_CACHE_TOLERANCE = 5000;
    private AccessibilityNodeInfo rootNodeInfo;
    private List<AccessibilityNodeInfo> mReceiveNode;
    private SharedPreferences sharedPreferences;
    private int watchType;
    private String[] watchMessage;
    private String[] watchBothMessage = new String[]{QQ_DEFAULT, QQ_DEFAULT_PASSWORD,
            QQ_CLICK_TO_ENTER_PASSWORD, WX_RED_PACKETS_GET, WX_GIVE_YOU_RED_PACKETS,WX_GET, QQ_SEND_BUTTON_TEXT};
    private String[] watchQQMessage = new String[]{QQ_DEFAULT, QQ_SEND_BUTTON_TEXT, QQ_CLICK_TO_ENTER_PASSWORD, QQ_SEND_BUTTON_TEXT};
    private String[] watchWXMessage = new String[]{WX_RED_PACKETS_GET, WX_GIVE_YOU_RED_PACKETS,WX_GET};


    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        switch (sharedPreferences.getString("watch_list", "")) {
            case "watch_both":
                watchType = 0;
                break;
            case "watch_qq":
                watchType = 1;
                break;
            case "watch_wx":
                watchType = 2;
                break;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        watchFlagsFromPreference();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        if (watchType == 1) {
            if ("com.tencent.mm".contentEquals(accessibilityEvent.getPackageName())) {
                return;
            }
        } else if (watchType == 2) {
            if ("com.tencent.mobileqq".contentEquals(accessibilityEvent.getPackageName())) {
                return;
            }
        }
        switch (accessibilityEvent.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                watchNotifications(accessibilityEvent);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                watchChat();
                break;
            default:
                break;
        }
    }


    private void watchNotifications(AccessibilityEvent event) {

        String tip = event.getText().toString();
        if (tip.contains("[微信红包]") || tip.contains("[QQ红包]")) {
            Parcelable parcelable = event.getParcelableData();
            if (parcelable instanceof Notification) {
                Notification notification = (Notification) parcelable;
                try {
                    notification.contentIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }


        }
    }

    private void watchChat() {
        this.rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) {
            return;
        }
        mReceiveNode = null;
        checkNodeInfo();
        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && (mReceiveNode != null)) {
            int size = mReceiveNode.size();
            if (size > 0) {
                AccessibilityNodeInfo cellNode = mReceiveNode.get(size - 1);
                String id = getHongbaoText(mReceiveNode.get(size - 1));
                long now = System.currentTimeMillis();
                if (!cellNode.getText().toString().contains("[QQ红包]")) {
                    if (shouldReturn(id, now - lastFetchedTime)) {
                        return;
                    }
                }
                lastFetchedHongbaoId = id;
                lastFetchedTime = now;

                if (cellNode.getText().toString().equals("红包已领取")) {
                    return;
                }

                if (WX_RED_PACKETS_GET.contains(cellNode.getText().toString())) {
                    cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
                if (WX_GIVE_YOU_RED_PACKETS.equals(cellNode.getText().toString())) {
                    if (cellNode.getParent().getChild(3) != null) {
                        cellNode.getParent().getChild(3).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                }
                if (cellNode.getText().toString().contains("[QQ红包]")) {
                    cellNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    //Log.d(TAG, "watchChat: 点击执行了");
                    return;
                }
                if (cellNode.getText().toString().equals("口令红包已拆开")) {
                    return;
                }
                cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (cellNode.getText().toString().contains(QQ_DEFAULT_PASSWORD)) {
                    AccessibilityNodeInfo rowNode = getRootInActiveWindow();
                    if (rowNode == null) {
                        return;
                    } else {
                        recycle(rowNode);
                    }
                }

                mLuckyMoneyReceived = false;
            }
        }
    }

    private String getHongbaoText(AccessibilityNodeInfo node) {
        /* 获取红包上的文本 */
        String content;
        try {
            AccessibilityNodeInfo i = node.getParent().getChild(0);
            content = i.getText().toString();
        } catch (NullPointerException npe) {
            return null;
        }
        return content;
    }

    /**
     * @param id       当前红包上的文字
     * @param duration 两次之间的时间间隔
     * @return
     */
    private boolean shouldReturn(String id, long duration) {

        if (id == null) {
            return true;
        }
        // 间隔时间小于5000ms并且两次红包上的文字一样直接返回true，不进行继续抢红包
        if (duration < MAX_CACHE_TOLERANCE && id.equals(lastFetchedHongbaoId)) {
            return true;
        }
        return false;
    }

    private void checkNodeInfo() {
        if (rootNodeInfo == null) {
            return;
        }

        switch (watchType){
            case 0:
                watchMessage = watchBothMessage;
                break;
            case 1:
                watchMessage = watchQQMessage;
                break;
            case 2:
                watchMessage = watchWXMessage;
                break;
        }

        // 聊天会话窗口，遍历节点匹配
        List<AccessibilityNodeInfo> nodes1 = findAccessibilityNodeInfosByTexts(this.rootNodeInfo,
                watchMessage);
        if (!nodes1.isEmpty()) {
            String nodeId = Integer.toHexString(System.identityHashCode(this.rootNodeInfo));
            if (!nodeId.equals(lastFetchedHongbaoId)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = nodes1;
            }
        }
    }

    /**
     * 遍历节点信息，只要参数texts数组中的一个匹配，则返回匹配到这些节点的list<AccessibilityNodeInfo>
     *
     * @param nodeInfo
     * @param texts
     * @return
     */
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            //  Log.d(TAG, "findAccessibilityNodeInfosByText: " + text);
            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);
            for (int i = 0; i < nodes.size(); i++) {
                // Log.d(TAG, "findAccessibilityNodeInfosByTexts: " + nodes.get(i).getText().toString());
            }
            if (!nodes.isEmpty()) {
                return nodes;
            }
        }
        return new ArrayList<>();
    }

    public void recycle(AccessibilityNodeInfo info) {
        if (info.getChildCount() == 0) {
            /*这个if代码的作用是：匹配“点击输入口令的节点，并点击这个节点”*/
            if (info.getText() != null && info.getText().toString().equals(QQ_CLICK_TO_ENTER_PASSWORD)) {
                info.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            /*这个if代码的作用是：匹配文本编辑框后面的发送按钮，并点击发送口令*/
            if (info.getClassName().toString().equals("android.widget.Button") && info.getText().toString().equals("发送")) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }


    @Override
    public void onInterrupt() {

    }

    private void watchFlagsFromPreference() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if ("watch_list".equals(s)) {
            switch (sharedPreferences.getString("watch_list", "")) {
                case "watch_both":
                    watchType = 0;
                    break;
                case "watch_qq":
                    watchType = 1;
                    break;
                case "watch_wx":
                    watchType = 2;
                    break;
            }
        }
    }
}
