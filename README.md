# Walkmate

Final Android Project of the subject Geoinformatics of the Master in Development of Applications and Services for Mobile Devices.

## Description

Application that serves as a mini social media platform to let you know where your friends and yourself have been with the help of an interactive map. We've made use of [MapBox](https://www.mapbox.com/) for the interactive maps for creating the posts and establishing routes between points.

For further use of this project, you must include in your `gradle.properties` your secret key of MapBox SDK and include it in the `gradle:project` under the credentials password label.

## Basic Functionalities

Here we describe the basic functionalities based on their location in the code.

### LogInActivity.kt

- Create user within Walkmate
- Log in with a previously created user

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/login.jpeg?raw=true" width="200" height="433">

### MainActivity.kt

- Posts of the users you follow will appear in chronological order. Each post consist of a location, user who posted it, title and date of creation. The location appears as a minimap in each post for basic location interpretation
- User can tap on a post's creator to navigate to the creator's profile
- User can interact with the posts by clicking on them to go to NavigationActivity
- User can go to new activity to add a post
- User can go to your profile
- User can go to search activity

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/feed.jpeg?raw=true" width="200" height="433">

### NavigationActivity.kt

Activity in which the details of a post are seen.

- Full map is displayed in screen with the post's location as the _goal_
- User can tap on the map for adding a marker to establish an automatic route between the marker and the goal
- User can search for an ambiguous address to automatically add a marker in there instead of searching it in the map
- User can enable the location to automatically establish a route between its current position and the goal
- User can switch between map modes anytime while maintaining the route and markers: OUTDOORS and SATELLITE
- When the user establishes a route, the ETA (Estimated Time of Arrival) and the distance between points are shown as additional information
- When the user establishes a route, he/she can switch route modes between CAR, BICYCLE or WALK to alternate routes with the same points. Info is updated
- Routes are automatically displayed on users tap or route mode selection
- When the user has enabled the location, the user can tap on the data button to go to a full view of step navigation view to actually go to the _goal_ from the user's location

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/navigation.jpeg?raw=true" width="200" height="433">
<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/map.jpeg?raw=true" width="200" height="433">

### NewPostActivity.kt

- Full map is displayed for the user to select a point for saving it to the user's profile and be displayed in the user's followers feed
- User can type a distinct title for the user to remember what the user did in the selected place
- User can search for an ambiguous address to automatically add a marker in there instead of searching it in the map
- User can enable the location to automatically locate itself in the map, as the map is established in Madrid, Spain by default
- User can switch between map modes anytime while maintaining the marker: OUTDOORS and SATELLITE

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/newpost.jpeg?raw=true" width="200" height="433">

### ProfileActivity.kt

- Close session
- User can see the number of followers/followees he or she has
- User can view his/her posts
- User can go to new activity to add a post
- If the profile is the current user's, user can delete any post he or she wants by swiping left or right
- If the profile is not the current user's, a FOLLOW or UNFOLLOW button is displayed to follow or unfollow said user

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/profile.jpeg?raw=true" width="200" height="433">

### SearchActivity.kt

- Users can search for other users by name and see their profiles

<img src="https://github.com/gabrielglbh/walkmate-geo-upm/blob/main/assets/search.jpeg?raw=true" width="200" height="433">
