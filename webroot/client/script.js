
var currentChannel = "default";
var username = JSON.parse(sessionStorage.userName); //retrieve the login from the login page
var getListChannelsTimer;
var getListMessageTimer;
// var  messageV = $("#TextZone"); -> Textzone est un id
// var  messageV = $(".TextZone"); -> TextZone est une classe


$(document).ready(function(){
			$("#currentUser").html(username);
			$("#currentChannel").html(currentChannel);
			initialize();
			setReloadInterval();
		});

function initialize(){
	textAreaDefaultValueDisappearOnClick();
	getListChannels();
	getListMessageForChannel();
	getListUsersForChan();
}

function setReloadInterval(){
    // Intervals will be much lower once live
    getListChannelsTimer = setInterval(getListChannels,10000);// 10 000 for debug
    getListMessageTimer = setInterval(getListMessageForChannel,10000); // 10 000 for debug
}

/* Permet d'avoir un texte par defaut dans la zone de d'ecriture de message et qui disparait lors du clique sur la zone */
function textAreaDefaultValueDisappearOnClick(){
	$('textArea#TextZone')
    .focus(function() {
        if (this.value === this.defaultValue) {
            this.value = '';
        }
	})
	.blur(function() {
        if (this.value === '') {
            this.value = this.defaultValue;
        }
	});
}




// Permet de creer un channel
// Et envoie l'information au serveur
// TODO
function createChannel(){
    var newChannelName = "";
	$.post("/api/private/createChannel",
	    JSON.stringify({channelName:newChannelName,creatorName:username}))
	    .done(function(response){
            alert("success create channel");
        })
        .fail(function(response){
            alert("fail create channel");
        })
        .always(function() {

        });
}





// TODO
function deleteChannel(){
    //target =
    var target = "";
	$.post("/api/private/deleteChannel",
	    JSON.stringify({channelName:target,user:username}))
	    .done(function(response){
            alert("success delete channel");
        })
        .fail(function(response){
            alert("fail delete channel");
        })
        .always(function() {

        });
}


// Fonctionne
function selectChannel(){
	var ul = $("#channels");
	ul.click(function(event) {
		var target = getEventTarget(event);
        var targetChannel = target.innerHTML;
        var oldChannel = $("#currentChannel").html();
        $.post("/api/private/connectToChannel",
            JSON.stringify({channelName : targetChannel,userName : username,oldChannelName : oldChannel}))
            .done(function (response){
                $("#currentChannel").html(targetChannel);
                getListUsersForChan();
                getListMessageForChannel();
            })
            .fail(function(response){
//                alert("fail connectToChannel");
            })
            .always(function() {

            });
	});
}

// L'utilisateur saisie un message
// Envoie le message au serveur qui l'ajoutera à la base de donné du channel
function sendMessage(){
    var  messageV = $('textArea#TextZone');
    $.post("/api/private/sendMessage",
	    JSON.stringify({channelName : currentChannel, message : messageV.val(),username : username}))
	    .done(function(response){
            alert("success send message");
            messageV.val("");
	    })
	    .fail(function(response){
            alert("fail send message");
		})
		.always(function(){

        });
}

function getListMessageForChannel(){
	var listMessage = $(".tchatIntern");
	var currentChannel = $("#currentChannel").html();

	$.post("/api/private/getListMessageForChannel",
		JSON.stringify({channelName:currentChannel,numberOfMessage:1000}))
	    .done(function(response){
				listMessage.children().remove();
	            var string = "";
	            $.each(response,function(key){
	                var date = response[key].date;
	            	var msg = response[key].content;
	            	var name = response[key].sender.name;
                    string = string + chatMessageFormatting(name,msg,date);
	            });
	            listMessage.append(string);
        })
        .fail(function(response){

        })
        .always(function() {

        });
}


function getListChannels(){
	var listChannel = $(".listChannelsIntern");

	$.get("/api/private/getListChannel")
	        .done(function(response){
				listChannel.children().remove();
				// Provoque un effet "On/Off" au chargement
				var string = "<ul id=\"channels\" onclick=\"selectChannel()\">"
                $.each(response,function(key,val){
                    string = string +"<li>"+ val +"</li>";
                });
                string = string + "</ul>";
                listChannel.append(string);

            })
            .fail(function(response){

            })
            .always(function() {

            });

}


// Fonctionne
function getListUsersForChan(){
	var usersListOnChan = $(".listUsersIntern");
    var currentChannel = $("#currentChannel").html();

	$.post("/api/private/getListUserForChannel",
	    JSON.stringify({channelName : currentChannel}))
	    .done(function(response){
			usersListOnChan.children().remove();
            var string ="<ul id=\"usersOnChan\">";
            $.each(response,function(key,val){
                string = string +"<li>"+ val+"</li>";
            });
            string = string + "</ul>";
            usersListOnChan.append(string);
        })
        .fail(function(response){

        })
        .always(function() {

        });
}

//TODO : voir comment repermettre le login apres deco
function disconnectFromServer(){
        var currentChannel = $("#currentChannel").html();
		$.post("/api/private/disconnectFromServer",
	    JSON.stringify({userName : username, currentChannelName:currentChannel}))
	    .done(function(response){
	    //Nettoyage des timer pour eviter d'effecteur des requetes alors que
	    //l'utilisateur s'est deco
	    window.clearInterval(getListChannelsTimer);
	    window.clearInterval(getListMessageTimer);
        window.location.href = "../index.html";
        })
        .fail(function(response){

        })
        .always(function() {

        });
	
}

//////// FONCTION OUTILS ALLEGEMENT CODE////////////

/*Fonction formattant une chaine de message en rajoutant
les informations de date et les balises HTML necessaires au
bon formattage.

Format actuel :
<p>hh:mm DD/MM/YYYY : <br> monMessage <br>

le split permet de recuperer les sauts de lignes et
y inserer les balises html adequat pour le formattage
*/
function chatMessageFormatting(username,msg,dateAsLong){
    var date = new Date(dateAsLong);
    var hours = addZero(date.getHours());
    var minutes = addZero(date.getMinutes());
    var day = addZero(date.getDate());
    var month = correctMonth(date);
    var year = date.getFullYear();
    if(msg.length > 512){
       msg=msg.slice(0,512);
    }
    /*return "<p>"+ date.parse("DD/MM/YYYY HH:mm ")+" ["+username+"] : "
                                              +"<br>"+msg.split("\n").join("<br>")+"</p>"+"<br>";*/
   return "<p>"+ hours+":"+minutes +" "+day+"/"+month+"/"+year+" "+username+" : "
                                   +"<br>"+msg.split("\n").join("<br>")+"</p>"+"<br>";
}

function addZero(i){
    if(i < 10){
        i = "0"+i;
    }
    return i;
}

function correctMonth(date){
    return addZero(date.getMonth()+1);
}

// Compatibilite pour IE si besoin
function getEventTarget(e) {
	e = e || window.event;
	return e.target || e.srcElement;
}