package pw.ifyr;

import pw.ifyr.R;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

public class CharIME extends InputMethodService implements
		OnKeyboardActionListener {
	private KeyboardView mInputView = null;

	private boolean mCapsLock = false;
	private long mLastShiftTime = 0L;

	private CharKeyboard mQwertyKeyboard = null;
	private CharKeyboard mSymbolsKeyboard = null;
	private CharKeyboard mSymbolsShiftedKeyboard = null;

	private CharKeyboard mCurKeyboard = null;

	@Override
	public void onInitializeInterface() {
		mQwertyKeyboard = new CharKeyboard(this, R.xml.qwerty);
		mSymbolsKeyboard = new CharKeyboard(this, R.xml.symbols);
		mSymbolsShiftedKeyboard = new CharKeyboard(this, R.xml.symbols_shift);
	}

	@Override
	public boolean onEvaluateFullscreenMode() {
		// ���ȫ��ģʽ
		return false;
	}

	@Override
	public View onCreateInputView() {
		// װ��keyboard.xml�ļ�
		mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input,
				null);
		mInputView.setPreviewEnabled(false);
		mInputView.setOnKeyboardActionListener(this);
		mInputView.setKeyboard(mQwertyKeyboard);
		// ����View����
		return mInputView;
	}

	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);

		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_DATETIME:
		case EditorInfo.TYPE_CLASS_PHONE:
			// ���ı������Ҫ���������ֻ�����ʱ����ʾ���������
			mCurKeyboard = mSymbolsKeyboard;
			break;
		case EditorInfo.TYPE_CLASS_TEXT:
		default:
			// ��ʾ������ĸ�������
			mCurKeyboard = mQwertyKeyboard;
			// ����Shift״̬
			updateShiftKeyState(attribute);
			break;
		}

		mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	@Override
	public void onFinishInput() {
		super.onFinishInput();

		mCurKeyboard = mQwertyKeyboard;
		if (mInputView != null) {
			mInputView.closing();
		}
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		mInputView.setKeyboard(mCurKeyboard);
	}

	@Override
	public void onKey(int primaryCode, int[] keyCodes) {
		if (primaryCode == Keyboard.KEYCODE_DELETE) { // ����ɾ������
			handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {// ����Shift����
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) { // ����Cancel����
			handleClose();
			return;
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
				&& mInputView != null) {
			Keyboard current = mInputView.getKeyboard();
			if (current == mSymbolsKeyboard
					|| current == mSymbolsShiftedKeyboard) {
				current = mQwertyKeyboard;
			} else {
				current = mSymbolsKeyboard;
			}
			mInputView.setKeyboard(current);
			if (current == mSymbolsKeyboard) {
				current.setShifted(false);
			}
		} else {
			if (isAlphabet(primaryCode) && mInputView.isShifted()) {
				primaryCode = Character.toUpperCase(primaryCode);
			}
			sendKey(primaryCode);
			updateShiftKeyState(getCurrentInputEditorInfo());
		}
	}

	private void keyDownUp(int keyEventCode) {
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}

	private void handleBackspace() {
		keyDownUp(KeyEvent.KEYCODE_DEL);
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	private void handleShift() {
		if (mInputView == null) {
			return;
		}

		Keyboard currentKeyboard = mInputView.getKeyboard();
		if (currentKeyboard == mSymbolsKeyboard) {
			// �л������ż���
			mSymbolsKeyboard.setShifted(true);
			mInputView.setKeyboard(mSymbolsShiftedKeyboard);
			mSymbolsShiftedKeyboard.setShifted(true);
		} else if (currentKeyboard == mSymbolsShiftedKeyboard) {
			mSymbolsShiftedKeyboard.setShifted(false);
			mInputView.setKeyboard(mSymbolsKeyboard);
			mSymbolsKeyboard.setShifted(false);
		} else { // currentKeyboard == mQwertyKeyboard
			// �л�����ĸ����
			checkToggleCapsLock();
			mInputView.setShifted(mCapsLock || !mInputView.isShifted());
		}
	}

	private void handleClose() {
		requestHideSelf(0);
		mInputView.closing();
	}

	private void sendKey(int keyCode) {
		if (keyCode == '\n') {
			keyDownUp(KeyEvent.KEYCODE_ENTER);
		} else if (keyCode >= '0' && keyCode <= '9') {
			keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
		} else {
			getCurrentInputConnection().commitText(
					String.valueOf((char) keyCode), 1);
		}
	}

	private void checkToggleCapsLock() {
		long now = System.currentTimeMillis();
		if (mLastShiftTime + 800 > now) {
			mCapsLock = true;
			mLastShiftTime = 0;
		} else {
			mCapsLock = false;
			if (mInputView.isShifted()) {
				mLastShiftTime = 0;
			} else {
				mLastShiftTime = now;
			}
		}
	}

	private void updateShiftKeyState(EditorInfo attr) {
		if (!mCapsLock && attr != null && mInputView != null
				&& mInputView.isShifted()
				&& mQwertyKeyboard == mInputView.getKeyboard()) {
			mInputView.setShifted(false);
		}
	}

	private boolean isAlphabet(int code) {
		return Character.isLetter(code);
	}

	@Override
	public void onPress(int primaryCode) {
	}

	@Override
	public void onRelease(int primaryCode) {
	}

	@Override
	public void onText(CharSequence text) {
		getCurrentInputConnection().commitText(text, 0);
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	@Override
	public void swipeLeft() {
		handleBackspace();
	}

	@Override
	public void swipeRight() {
		keyDownUp(KeyEvent.KEYCODE_ENTER);
	}

	@Override
	public void swipeDown() {
		handleClose();
	}

	@Override
	public void swipeUp() {
		handleShift();
	}
}
