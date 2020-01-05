package com.android.settings;

public class ApnSetting {
	private String mNameStr;
	private String mApnStr;
	private String mProxyStr;
	private String mPortStr;
	private String mUserStr;
	private String mServerStr;
	private String mPasswordStr;
	private String mMmscStr;
	private String mMccStr;
	private String mMncStr;
	private String mMmsProxyStr;
	private String mMmsPortStr;
	private String mAuthTypeStr;
	private String mApnTypeStr;
	private String mProtocolStr;
	private String mRoamingProtocolStr;
	private String mBearerStr;
	private String mMvnoTypeStr;
	private String mMvnoMatchDataStr;
	public ApnSetting(){}
	public String getmNameStr() {
		return mNameStr;
	}
	public void setmNameStr(String mNameStr) {
		this.mNameStr = mNameStr;
	}
	public String getmApnStr() {
		return mApnStr;
	}
	public void setmApnStr(String mApnStr) {
		this.mApnStr = mApnStr;
	}
	public String getmProxyStr() {
		return mProxyStr;
	}
	public void setmProxyStr(String mProxyStr) {
		this.mProxyStr = mProxyStr;
	}
	public String getmPortStr() {
		return mPortStr;
	}
	public void setmPortStr(String mPortStr) {
		this.mPortStr = mPortStr;
	}
	public String getmUserStr() {
		return mUserStr;
	}
	public void setmUserStr(String mUserStr) {
		this.mUserStr = mUserStr;
	}
	public String getmServerStr() {
		return mServerStr;
	}
	public void setmServerStr(String mServerStr) {
		this.mServerStr = mServerStr;
	}
	public String getmPasswordStr() {
		return mPasswordStr;
	}
	public void setmPasswordStr(String mPasswordStr) {
		this.mPasswordStr = mPasswordStr;
	}
	public String getmMmscStr() {
		return mMmscStr;
	}
	public void setmMmscStr(String mMmscStr) {
		this.mMmscStr = mMmscStr;
	}
	public String getmMccStr() {
		return mMccStr;
	}
	public void setmMccStr(String mMccStr) {
		this.mMccStr = mMccStr;
	}
	public String getmMncStr() {
		return mMncStr;
	}
	public void setmMncStr(String mMncStr) {
		this.mMncStr = mMncStr;
	}
	public String getmMmsProxyStr() {
		return mMmsProxyStr;
	}
	public void setmMmsProxyStr(String mMmsProxyStr) {
		this.mMmsProxyStr = mMmsProxyStr;
	}
	public String getmMmsPortStr() {
		return mMmsPortStr;
	}
	public void setmMmsPortStr(String mMmsPortStr) {
		this.mMmsPortStr = mMmsPortStr;
	}
	public String getmAuthTypeStr() {
		return mAuthTypeStr;
	}
	public void setmAuthTypeStr(String mAuthTypeStr) {
		this.mAuthTypeStr = mAuthTypeStr;
	}
	public String getmApnTypeStr() {
		return mApnTypeStr;
	}
	public void setmApnTypeStr(String mApnTypeStr) {
		this.mApnTypeStr = mApnTypeStr;
	}
	public String getmProtocolStr() {
		return mProtocolStr;
	}
	public void setmProtocolStr(String mProtocolStr) {
		this.mProtocolStr = mProtocolStr;
	}
	public String getmRoamingProtocolStr() {
		return mRoamingProtocolStr;
	}
	public void setmRoamingProtocolStr(String mRoamingProtocolStr) {
		this.mRoamingProtocolStr = mRoamingProtocolStr;
	}
	public String getmBearerStr() {
		return mBearerStr;
	}
	public void setmBearerStr(String mBearerStr) {
		this.mBearerStr = mBearerStr;
	}
	public String getmMvnoTypeStr() {
		return mMvnoTypeStr;
	}
	public void setmMvnoTypeStr(String mMvnoTypeStr) {
		this.mMvnoTypeStr = mMvnoTypeStr;
	}
	public String getmMvnoMatchDataStr() {
		return mMvnoMatchDataStr;
	}
	public void setmMvnoMatchDataStr(String mMvnoMatchDataStr) {
		this.mMvnoMatchDataStr = mMvnoMatchDataStr;
	}
	@Override
	public String toString() {
		return "ApnSetting [mNameStr=" + mNameStr + ", mApnStr=" + mApnStr
				+ ", mProxyStr=" + mProxyStr + ", mPortStr=" + mPortStr
				+ ", mUserStr=" + mUserStr + ", mServerStr=" + mServerStr
				+ ", mPasswordStr=" + mPasswordStr + ", mMmscStr=" + mMmscStr
				+ ", mMccStr=" + mMccStr + ", mMncStr=" + mMncStr
				+ ", mMmsProxyStr=" + mMmsProxyStr + ", mMmsPortStr="
				+ mMmsPortStr + ", mAuthTypeStr=" + mAuthTypeStr
				+ ", mApnTypeStr=" + mApnTypeStr + ", mProtocolStr="
				+ mProtocolStr + ", mRoamingProtocolStr=" + mRoamingProtocolStr
				+ ", mBearerStr=" + mBearerStr + ", mMvnoTypeStr="
				+ mMvnoTypeStr + ", mMvnoMatchDataStr=" + mMvnoMatchDataStr
				+ "]";
	}
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}else {
			if (this.getClass() == obj.getClass()) {
				ApnSetting newApnSetting = (ApnSetting) obj;
				if (this.getmNameStr().equals(newApnSetting.getmNameStr()) &&
					this.getmApnStr().equals(newApnSetting.getmApnStr()) &&
					this.getmProxyStr().equals(newApnSetting.getmProxyStr()) &&
					this.getmPortStr().equals(newApnSetting.getmPortStr()) &&
					this.getmUserStr().equals(newApnSetting.getmUserStr()) &&
					this.getmServerStr().equals(newApnSetting.getmServerStr()) &&
					this.getmPasswordStr().equals(newApnSetting.getmPasswordStr()) &&
					this.getmMmsProxyStr().equals(newApnSetting.getmMmsProxyStr()) &&
					this.getmMmsPortStr().equals(newApnSetting.getmMmsPortStr()) &&
					this.getmMmscStr().equals(newApnSetting.getmMmscStr()) &&
					this.getmMccStr().equals(newApnSetting.getmMccStr()) &&
					this.getmMncStr().equals(newApnSetting.getmMncStr()) &&
					this.getmAuthTypeStr().equals(newApnSetting.getmAuthTypeStr()) &&
					this.getmApnTypeStr().equals(newApnSetting.getmApnTypeStr()) &&
					this.getmProtocolStr().equals(newApnSetting.getmProtocolStr()) &&
					this.getmRoamingProtocolStr().equals(newApnSetting.getmRoamingProtocolStr()) &&
					this.getmBearerStr().equals(newApnSetting.getmBearerStr()) &&
					this.getmMvnoTypeStr().equals(newApnSetting.getmMvnoTypeStr()) &&
					this.getmMvnoMatchDataStr().equals(newApnSetting.getmMvnoMatchDataStr())) {
					return true;
				}else {
					return false;
				}
			}else {
				return false;
			}
		}
	}
}
