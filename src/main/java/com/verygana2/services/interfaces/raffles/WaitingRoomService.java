package com.verygana2.services.interfaces.raffles;

public interface WaitingRoomService {
    
    void addViewer (Long raffleId, String sessionId);
    void removeViewer (Long raffleId, String sessionId);
    int getViewerCount (Long raffleId);
    void clearRoom (Long raffleId);
    void broadcastWaitingRoomUpdates();
    void removeViewerFromAllRooms(String sessionId);
}
