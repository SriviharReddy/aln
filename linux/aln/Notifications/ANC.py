from ..enums import enums
from ..Capabilites import Capabilites
class ANCNotification:
    NOTIFICATION_PREFIX = enums.NOISE_CANCELLATION_PREFIX
    OFF = Capabilites.NoiseCancellation.OFF
    ON = Capabilites.NoiseCancellation.ON
    TRANSPARENCY = Capabilites.NoiseCancellation.TRANSPARENCY
    ADAPTIVE = Capabilites.NoiseCancellation.ADAPTIVE
    
    def __init__(self):
        pass
    
    def isANCData(self, data: bytes):
        # 04 00 04 00 09 00 0D 01 00 00 00
        if len(data) != 11:
            return False
        
        if data.hex().startswith(self.NOTIFICATION_PREFIX.hex()):
            return True
        else:
            return False
        
    def setData(self, data: bytes):
        self.status = data[7]
        pass