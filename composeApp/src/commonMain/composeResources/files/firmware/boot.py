import uos, machine
import network
import config
import gc
import config
import server
import utime
PORT = 65432
SERIAL_NUMBER,_,_ = config.getDeviceInfo()
AP_NAME ="TOAF-" + SERIAL_NUMBER
AP_PASSWORD = config.getApPassword()

def boot():
     AccesPointRun()
     WlanRun()
     gc.collect()
     server.RunServer(config.getServerSetting(),PORT)
def AccesPointRun():
     accesPoint = network.WLAN(network.AP_IF)
     accesPoint.active(True)
     accesPoint.config(essid = AP_NAME,password = AP_PASSWORD, channel = 4, authmode=3)
def WlanRun():
     try:
          ssid, password = config.getWlanSettings()
          if not ssid:
               return
          wlan = network.WLAN(network.STA_IF)
          wlan.active(True)
          wlan.config(dhcp_hostname=AP_NAME)
          wlan.connect(ssid, password)
          timeout = 0
          while not wlan.isconnected():
               utime.sleep_ms(500)
               timeout += 500
               if timeout > 15000:
                    wlan.active(False)
                    break
     except Exception as inst:
          try:
               network.WLAN(network.STA_IF).active(False)
          except:
               pass
     
if __name__ == '__main__':        
    boot()