Feature: Concert Booking E2E Flow
  Verify the complete booking flow from queue entry to payment

  Background:
    Given Core service is healthy
    And Queue service is healthy
    And Test data is prepared

  Scenario: Successful booking flow (Queue → Seats → Reservation → Payment → Auto Queue Removal)
    Given User "17669445080001234" requests to enter queue for concert "1"
    When User sends queue entry request
    Then Queue entry is successful
    And User receives queue position

    When User polls queue status
    Then User becomes "READY" within 2 minutes
    And User receives queue token

    When User queries seats for schedule "1"
    Then Seats query is successful
    And Available seats exist

    When User selects first available seat
    And User requests seat reservation
    Then Seat reservation is successful
    And User receives reservation ID

    When User requests payment for reservation
    Then Payment is successful
    And User receives payment ID

    When System waits for Kafka event processing
    Then User is automatically removed from queue within 10 seconds
