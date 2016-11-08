

//////// TOUT FAIRE EN AJAX ////////////

var currentChannel = "default";
var numberChannels = 0;
var numberUsers = 0;
var username = "blork";

$(document).ready(function(){
//			$('#paragraphe').html(valeur1);
			$("#currentUser").html(username);
			$("#currentChannel").html(currentChannel);
		});

// L'utilisateur saisie un message
// Envoie le message au serveur qui l'ajoutera à la base de donné du channel
function sendMessage(){
	var messageV= $("#textEntry");

	$.post("/sendMessage",JSON.stringify({channel : currentChannel, message : messageV.val(),username : username}),
			function(){

//				element.append(data);
			},"json");
}

// Permet de creer un channel
// Et envoie l'information au serveur
function createChannel(){

	$.post("/createChannel",
		function(data){

		});
}

function getListUsers(){



}

// TODO A changer !
function getListChannels(){
	var listChannel = $("#listChannels");
	listChannel.children().remove();

	//var delimiter = "/"
	// format : $.get(nomRequete,fonction a appliquer)
	/*$.get("/getListChannels",function(data){
			// Mettre un delimiteur entre chaque nom de channel pour split
			// Exemple :
			//   MonSuperChannel / UnAutreChannel / EncoreUn
			var tab = data.split(delimiter)
			tab.foreach(printElements)
			for(val in tab){

			}
		}
	)*/
	// TODO verifier fonctionnement

	// Va creer une liste cliquable avec chacun des channel
	listChannel.append("<ul id=\"channels\" onclick=\"selectChannel()\">");
	$.getJSON("/api/getListChannels",function(data){
		$.each(data,function(val){
			listChannel.append("<li> "+ val+" </li>");
		})
	});
	listChannel.append("</ul>");
}

// TODO A changer !
function getListUsersFromChan(){
	var usersListOnChan = $("#listUsers");
	usersListOnChan.children().remove();

	usersListOnChan.append("<ul id=\"usersOnChan\">");
	$.getJSON("/api/getListUsersForChanChannels/",function(data){
		$.each(data,function(val){
			usersListOnChan.append("<li> "+ val+" </li>");
		})
	});
	usersListOnChan.append("</ul>");
}

// Compatibilite pour IE si besoin
function getEventTarget(e) {
	e = e || window.event;
	return e.target || e.srcElement;
}

// Fonctionne
function selectChannel(){
//	var ul = document.getElementById('channels');
	var ul = $("#channels");
	ul.click(function(event) {
		var target = getEventTarget(event);
		var tmpChannel = target.innerHTML;
		var oldChannel = currentChannel;
        if (!(tmpChannel == currentChannel)){
            currentChannel=target.innerHTML;
        }
        else if (currentChannel == ""){
            currentChannel=target.innerHTML;
        }
        else{
            return
        }

        $.post("/api/connectToChannel",
            JSON.stringify({channelName : currentChannel,userName: username,oldChannelName : oldChannel}),
            function (reponse){
                alert("alacon");
            });
        alert(currentChannel);
	});
	
}
// TODO -> Test uniquement
function testAjax() {
		var username ="testify"
		$.get("/api/test/"+username, JSON.stringify({username: username}), function () {
				//load();
				// Do stuff
				alert("bla")
		},"json");
	}

function testAjax3() {
  	alert("a");
    $.post("/api/testJson/", JSON.stringify({currentChannelName: currentChannel}), function () {
   	    //load();
   		// Do stuff
   	    alert("bla")
    },"json");
}