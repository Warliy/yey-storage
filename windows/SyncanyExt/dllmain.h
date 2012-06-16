// dllmain.h : Declaration of module class.

class CSyncanyExtModule : public ATL::CAtlDllModuleT< CSyncanyExtModule >
{
public :
	DECLARE_LIBID(LIBID_SyncanyExtLib)
	DECLARE_REGISTRY_APPID_RESOURCEID(IDR_SYNCANYEXT, "{DE7F260C-F919-4A15-A6B3-25410E487B1C}")
};

extern class CSyncanyExtModule _AtlModule;
